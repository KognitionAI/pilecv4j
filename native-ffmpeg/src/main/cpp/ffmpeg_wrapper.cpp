#include "pilecv4j_utils.h"
#include "imagemaker.h"

extern "C" 
{
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/avutil.h"
#include "libavutil/error.h"
#include <libswscale/swscale.h>
}
#include <stdlib.h>

#undef av_err2str
#define av_err2str(errnum) av_make_error_string((char*)__builtin_alloca(AV_ERROR_MAX_STRING_SIZE),AV_ERROR_MAX_STRING_SIZE, errnum)

typedef void (*push_frame)(uint64_t frame, int32_t isRgb);

static bool loggingEnabled = false;

#ifdef LOGGING
inline static void logging(const char *fmt, ...)
{
  if (loggingEnabled) {
    va_list args;
    fprintf( stderr, "Ffmpeg_wrapper: " );
    va_start( args, fmt );
    vfprintf( stderr, fmt, args );
    va_end( args );
    fprintf( stderr, "\n" );
  }
}
#else
inline static void logging(...) {}
#endif

enum StreamContextState {
  FRESH,
  OPEN,
  CODEC
};

struct StreamContext {
  AVFormatContext* formatCtx = nullptr;
  AVCodecContext* codecCtx = nullptr;
  SwsContext* colorCvrt = nullptr;

  int streamIndex = -1;

  StreamContextState state = FRESH;

  inline ~StreamContext() {
    if (colorCvrt != nullptr)
      sws_freeContext(colorCvrt);
    if (codecCtx != nullptr)
      avcodec_free_context(&codecCtx);
    if (formatCtx != nullptr)
      avformat_free_context(formatCtx);
  }
};

enum Pcv4jStat {
  OK = 0,
  STREAM_IN_USE = 1,
  STREAM_BAD_STATE = 2,
  NO_VIDEO_STREAM = 3,
  NO_SUPPORTED_CODEC = 4,
  FAILED_CREATE_CODEC_CONTEXT = 5,
  FAILED_CREATE_FRAME = 6,
  FAILED_CREATE_PACKET = 7,
  LOGGING_NOT_COMPILED = 8
};
#define MAX_PCV4J_CODE 8

static const char* pcv4jStatMessages[MAX_PCV4J_CODE + 1] = {
    "OK",
    "Can't open another stream with the same context",
    "Context not in correct statr for given operation",
    "Couldn't find a video stream in the given source",
    "No supported video codecs available for the given source",
    "Failed to create a codec context",
    "Failed to create a frame",
    "Failed to create a packet",
    "Logging isn't compiled in this instance."
};

static const char* totallyUnknownError = "UNKNOWN ERROR";

#define MAKE_AV_STAT(x) ((uint64_t)((int32_t)x) & 0xffffffff)
#define MAKE_P_STAT(x) ((((uint64_t)((int32_t)x) & 0xffffffff)) << 32);

static inline bool isError(uint64_t stat) {
  // 0 is good and we expect this most of the time so check it first.
  if (stat == 0)
    return false;
  // if the MSBs are non zero then there's a PCV4J error
  if (stat & 0xffffffff00000000L)
    return true;
  // if the LSBs contain negative value then there's an AV error.
  if (stat & 0x0000000080000000L)
    return true;
  return false;
}

static uint64_t findFirstVidCodec(AVFormatContext* pFormatContext, AVCodec** pCodec,AVCodecParameters** pCodecParameters, int* video_stream_index);
static uint64_t decode_packet(AVCodecContext *pCodecContext, AVFrame *pFrame, AVPacket *pPacket, push_frame callback, SwsContext** colorCvrt);

static ai::kognition::pilecv4j::ImageMaker* imaker;

extern "C" {

  int ffmpeg_init() {
    return 0;
  }

  char* ffmpeg_statusMessage(uint64_t status) {
    // if the MSBs have a value, then that's what we're going with.
    {
      uint32_t pcv4jCode = (status >> 32) & 0xffffffff;
      if (pcv4jCode != 0) {
        if (pcv4jCode < 0 || pcv4jCode > MAX_PCV4J_CODE)
          return strdup(totallyUnknownError);
        else
          return strdup(pcv4jStatMessages[pcv4jCode]);
      }

    }
    char* ret = new char[AV_ERROR_MAX_STRING_SIZE + 1]{0};
    av_strerror(status, ret, AV_ERROR_MAX_STRING_SIZE);
    return ret;
  }

  void ffmpeg_freeString(char* str) {
    if (str)
      delete[] str;
  }

  uint64_t ffmpeg_createContext() {
    return (uint64_t) new StreamContext();
  }

  void ffmpeg_deleteContext(uint64_t ctx) {
    StreamContext* c = (StreamContext*)ctx;
    delete c;
  }

  uint64_t ffmpeg_openStream(uint64_t ctx, const char* url) {
    StreamContext* c = (StreamContext*)ctx;
    if (c->state != FRESH)
      return MAKE_P_STAT(STREAM_BAD_STATE);

    if (c->formatCtx)
      return MAKE_P_STAT(STREAM_IN_USE);

    c->formatCtx = avformat_alloc_context();

    uint64_t ret =  MAKE_AV_STAT(avformat_open_input(&c->formatCtx, url, nullptr, nullptr));
    if (!isError(ret))
      c->state = OPEN;
    return ret;
  }

  uint64_t ffmpeg_findFirstVideoStream(uint64_t ctx) {
    StreamContext* c = (StreamContext*)ctx;
    if (c->state != OPEN)
      return MAKE_P_STAT(STREAM_BAD_STATE);

    uint64_t stat = MAKE_AV_STAT(avformat_find_stream_info(c->formatCtx, nullptr));
    if (isError(stat))
      return stat;

    // the component that knows how to enCOde and DECode the stream
    // it's the codec (audio or video)
    // http://ffmpeg.org/doxygen/trunk/structAVCodec.html
    AVCodec *pCodec = NULL;
    // this component describes the properties of a codec used by the stream i
    // https://ffmpeg.org/doxygen/trunk/structAVCodecParameters.html
    AVCodecParameters *pCodecParameters =  NULL;
    int video_stream_index = -1;

    AVFormatContext* pFormatContext = c->formatCtx;

    stat = findFirstVidCodec(pFormatContext, &pCodec, &pCodecParameters, &video_stream_index);
    if (isError(stat))
      return stat;

    // https://ffmpeg.org/doxygen/trunk/structAVCodecContext.html
    c->codecCtx = avcodec_alloc_context3(pCodec);
    if (!c->codecCtx)
    {
      logging("failed to allocated memory for AVCodecContext");
      return MAKE_P_STAT(FAILED_CREATE_CODEC_CONTEXT);
    }
    AVCodecContext* pCodecContext = c->codecCtx;

    // Fill the codec context based on the values from the supplied codec parameters
    // https://ffmpeg.org/doxygen/trunk/group__lavc__core.html#gac7b282f51540ca7a99416a3ba6ee0d16
    stat = MAKE_AV_STAT(avcodec_parameters_to_context(pCodecContext, pCodecParameters));
    if (isError(stat))
      return stat;

    // Initialize the AVCodecContext to use the given AVCodec.
    // https://ffmpeg.org/doxygen/trunk/group__lavc__core.html#ga11f785a188d7d9df71621001465b0f1d
    stat = avcodec_open2(pCodecContext, pCodec, NULL);
    if (isError(stat))
    {
      logging("failed to open codec through avcodec_open2");
      return stat;
    }

    c->streamIndex = video_stream_index;
    c->state = CODEC;

    return stat;
  }

  uint64_t process_frames(uint64_t ctx, push_frame callback) {
    StreamContext* c = (StreamContext*)ctx;
    if (c->state != CODEC)
      return MAKE_P_STAT(STREAM_BAD_STATE);

    // https://ffmpeg.org/doxygen/trunk/structAVFrame.html
    AVFrame *pFrame = av_frame_alloc();
    if (!pFrame)
    {
      logging("failed to allocated memory for AVFrame");
      return MAKE_P_STAT(FAILED_CREATE_FRAME);
    }
    // https://ffmpeg.org/doxygen/trunk/structAVPacket.html
    AVPacket *pPacket = av_packet_alloc();
    if (!pPacket)
    {
      logging("failed to allocated memory for AVPacket");
      return MAKE_P_STAT(FAILED_CREATE_PACKET);
    }

    uint64_t response = 0;

    AVCodecContext* pCodecContext = c->codecCtx;
    AVFormatContext* pFormatContext = c->formatCtx;
    const int video_stream_index = c->streamIndex;

    // fill the Packet with data from the Stream
    // https://ffmpeg.org/doxygen/trunk/group__lavf__decoding.html#ga4fdb3084415a82e3810de6ee60e46a61
    int lastResult = 0;
    SwsContext** colorCvrt = &(c->colorCvrt);
    while ((lastResult = av_read_frame(pFormatContext, pPacket)) >= 0)
    {
      // if it's the video stream
      if (pPacket->stream_index == video_stream_index) {
        logging("AVPacket->pts %" PRId64, pPacket->pts);
        response = decode_packet(pCodecContext, pFrame, pPacket, callback, colorCvrt);
        if (isError(response))
          break;
      }
      // https://ffmpeg.org/doxygen/trunk/group__lavc__packet.html#ga63d5a489b419bd5d45cfd09091cbcbc2
      av_packet_unref(pPacket);
    }

    logging("Last result of read was: %s", av_err2str(lastResult));

    logging("releasing all the resources");

    av_packet_free(&pPacket);
    av_frame_free(&pFrame);

    return 0;
  }

  // exposed to java
  void set_im_maker(uint64_t im) {
    imaker = (ai::kognition::pilecv4j::ImageMaker*)im;
  }

  uint64_t enable_logging(int32_t toEnable) {
#ifdef LOGGING
    loggingEnabled = toEnable ? true : false;
    return 0;
#else
    return MAKE_P_STAT(LOGGING_NOT_COMPILED);
#endif
  }
}

static uint64_t findFirstVidCodec(AVFormatContext* pFormatContext, AVCodec** pCodec,AVCodecParameters** pCodecParameters, int* rvsi) {
  int video_stream_index = -1;

  bool foundUnsupportedCode = false;

  // loop though all the streams and print its main information
  for (int i = 0; i < pFormatContext->nb_streams; i++)
  {
    AVCodecParameters *pLocalCodecParameters =  NULL;
    pLocalCodecParameters = pFormatContext->streams[i]->codecpar;
    logging("AVStream->time_base before open coded %d/%d", pFormatContext->streams[i]->time_base.num, pFormatContext->streams[i]->time_base.den);
    logging("AVStream->r_frame_rate before open coded %d/%d", pFormatContext->streams[i]->r_frame_rate.num, pFormatContext->streams[i]->r_frame_rate.den);
    logging("AVStream->start_time %" PRId64, pFormatContext->streams[i]->start_time);
    logging("AVStream->duration %" PRId64, pFormatContext->streams[i]->duration);

    logging("finding the proper decoder (CODEC)");

    AVCodec *pLocalCodec = NULL;

    // finds the registered decoder for a codec ID
    // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga19a0ca553277f019dd5b0fec6e1f9dca
    pLocalCodec = avcodec_find_decoder(pLocalCodecParameters->codec_id);

    if (pLocalCodec==NULL) {
      logging("ERROR unsupported codec!");
      foundUnsupportedCode = true;
      continue;
    }

    // when the stream is a video we store its index, codec parameters and codec
    if (pLocalCodecParameters->codec_type == AVMEDIA_TYPE_VIDEO) {
      if (video_stream_index == -1) {
        video_stream_index = i;
        *pCodec = pLocalCodec;
        *pCodecParameters = pLocalCodecParameters;
      }

      logging("Video Codec: resolution %d x %d", pLocalCodecParameters->width, pLocalCodecParameters->height);
    } else if (pLocalCodecParameters->codec_type == AVMEDIA_TYPE_AUDIO) {
      logging("Audio Codec: %d channels, sample rate %d", pLocalCodecParameters->channels, pLocalCodecParameters->sample_rate);
    }

    // print its name, id and bitrate
    logging("\tCodec %s ID %d bit_rate %lld", pLocalCodec->name, pLocalCodec->id, pLocalCodecParameters->bit_rate);
  }

  if (video_stream_index == -1) {
    if (foundUnsupportedCode) {
      return MAKE_P_STAT(NO_SUPPORTED_CODEC);
    } else {
      return MAKE_P_STAT(NO_VIDEO_STREAM);
    }
  }

  *rvsi = video_stream_index;

  return 0;
}

static uint64_t decode_packet( AVCodecContext *pCodecContext, AVFrame *pFrame, AVPacket *pPacket, push_frame callback, SwsContext** colorCvrt)
{
  // Supply raw packet data as input to a decoder
  // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga58bc4bf1e0ac59e27362597e467efff3
  int response = avcodec_send_packet(pCodecContext, pPacket);

  if (response < 0 && response != AVERROR_INVALIDDATA) {
    logging("Error while sending a packet to the decoder: %s", av_err2str(response));
    return MAKE_AV_STAT(response);
  }

  while (response >= 0)
  {
    // Return decoded output data (into a frame) from a decoder
    // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga11e6542c4e66d3028668788a1a74217c
    response = avcodec_receive_frame(pCodecContext, pFrame);
    if (response == AVERROR(EAGAIN) || response == AVERROR_EOF) {
      break;
    } else if (response < 0) {
      logging("Error while receiving a frame from the decoder: %s", av_err2str(response));
      return MAKE_AV_STAT(response);
    }

    if (response >= 0) {
      logging(
          "Frame %d (type=%c, size=%d bytes, format=%d) pts %d key_frame %d [DTS %d]",
          pCodecContext->frame_number,
          av_get_picture_type_char(pFrame->pict_type),
          pFrame->pkt_size,
          pFrame->format,
          pFrame->pts,
          pFrame->key_frame,
          pFrame->coded_picture_number
      );

      uint8_t* matData;
      bool needToFree;

      const int32_t w = pFrame->width;
      const int32_t h = pFrame->height;
      const AVPixelFormat curFormat = (AVPixelFormat)pFrame->format;
      int32_t isRgb;

      if (curFormat != AV_PIX_FMT_RGB24 && curFormat != AV_PIX_FMT_BGR24) {
        SwsContext* swCtx = *colorCvrt;
        if (swCtx == nullptr) {
          *colorCvrt = swCtx =
              sws_getContext(
                w,
                h,
                curFormat,
                w,
                h,
                AV_PIX_FMT_RGB24,
                SWS_BILINEAR,NULL,NULL,NULL
              );
        }

        int32_t stride = 3 * w;
        matData = (uint8_t*)malloc(stride * h * sizeof(uint8_t));
        needToFree = true;
        uint8_t *rgb24[1] = { matData };
        int rgb24_stride[1] = { stride };
        sws_scale(swCtx,pFrame->data, pFrame->linesize, 0, h, rgb24, rgb24_stride);
        isRgb = 1;
      } else {
        matData = pFrame->data[0];
        needToFree = false;
        isRgb = (curFormat == AV_PIX_FMT_RGB24) ? 1 : 0;
      }

      uint64_t mat = imaker->makeImage(h,w,w * 3,matData);
      (*callback)(mat, isRgb);
      imaker->freeImage(mat);

      if (needToFree)
        free(matData);
    }
  }
  return 0;
}

