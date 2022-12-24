/*
 * DecodedFrameProcessor.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "processors/DecodedFrameProcessor.h"

#include "utils/IMakerManager.h"
#include "utils/log.h"

#include "common/kog_exports.h"

extern "C" {
#include <libavutil/opt.h>
}

#include <chrono>
#include <thread>

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "DEFP"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

struct CodecDetails {
  /**
   * Codec context.
   */
  AVCodecContext* codecCtx = nullptr;

  /**
   * Color converter to BGR/RGB. Available only after beginning play
   */
  SwsContext* colorCvrt = nullptr;

  AVPixelFormat lastFormatUsed = AV_PIX_FMT_NONE;

  AVMediaType mediaType;

  inline void close() {
    if (colorCvrt != nullptr)
      sws_freeContext(colorCvrt);
    if (codecCtx != nullptr)
      avcodec_free_context(&codecCtx);
  }

  inline ~CodecDetails() = default;
};

uint64_t DecodedFrameProcessor::close() {
  if (codecs) {
    for (int i = 0; i < numStreams; i++) {
      CodecDetails* cd = codecs[i];
      if (cd) {
        delete cd;
        codecs[i] = nullptr;
      }
    }

    delete [] codecs;
    codecs = nullptr;
  }
  return 0;
}

uint64_t DecodedFrameProcessor::setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams) {
  int numStreams = avformatCtx->nb_streams;

  if (numStreams <= 0)
    return MAKE_P_STAT(NO_STREAM);

  codecs = new CodecDetails*[numStreams];
  for (int i = 0; i < numStreams; i++) {
    codecs[i] = nullptr;

    if (streamSelected(selectedStreams, i)) {
      AVStream* lStream = avformatCtx->streams[i];

      if (!lStream) {
        llog(WARN, "The %d stream in the context is selected but doesn't appear to exist. It will be skipped.", i);
        continue;
      }

      AVCodecParameters *pLocalCodecParameters = lStream->codecpar;

      // check if the decoder exists
      {
        AVCodec *pLocalCodec = NULL;

        // finds the registered decoder for a codec ID
        // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga19a0ca553277f019dd5b0fec6e1f9dca
        pLocalCodec = avcodec_find_decoder(pLocalCodecParameters->codec_id);
        if (pLocalCodec==NULL) {
          llog(WARN, "ERROR unsupported codec at %d!", i);
          continue;
        }
      }

      codecs[i] = new CodecDetails();
      codecs[i]->mediaType = pLocalCodecParameters->codec_type;

      AVDictionary* opts = nullptr;
      buildOptions(options, &opts);
      uint64_t rc = MediaProcessor::open_codec(avformatCtx,i,&opts,&(codecs[i]->codecCtx));
      if (opts != nullptr)
        av_dict_free(&opts);
      if (isError(rc))
        return rc;
    }
  }

  return 0;
}

uint64_t DecodedFrameProcessor::handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType mediaType) {
  if (codecs == nullptr) {
    llog(ERROR, "handle packet called on uninitialized DecodedFrameProcessor");
    return MAKE_P_STAT(NO_SUPPORTED_CODEC);
  }
  return decode_packet(codecs[pPacket->stream_index], pPacket);
}

uint64_t DecodedFrameProcessor::preFirstFrame(AVFormatContext* avformatCtx) {
  return 0;
}

uint64_t DecodedFrameProcessor::decode_packet(CodecDetails* codecDetails, AVPacket *pPacket) {
  if (codecDetails == nullptr) {
    llog(WARN,"A null codecDetails was passed to decode_packet. Skipping.");
    return 0;
  }
  if (codecDetails->mediaType != AVMEDIA_TYPE_VIDEO) {
    llog(WARN, "Skipping non-video stream. Media type is %d", (int)codecDetails->mediaType);
    return 0;
  }
  // Supply raw packet data as input to a decoder
  // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga58bc4bf1e0ac59e27362597e467efff3
  int response = avcodec_send_packet(codecDetails->codecCtx, pPacket);

  if (response < 0 && response != AVERROR_INVALIDDATA) {
    llog(ERROR, "Error while sending a packet to the decoder: %s", av_err2str(response));
    return MAKE_AV_STAT(response);
  }

  // https://ffmpeg.org/doxygen/trunk/structAVFrame.html
  AVFrame *pFrame = av_frame_alloc();
  if (!pFrame)
  {
    llog(ERROR, "failed to allocated memory for AVFrame");
    return MAKE_P_STAT(FAILED_CREATE_FRAME);
  }

  uint64_t returnCode = 0;
  while (response >= 0 && returnCode == 0)
  {
    // Return decoded output data (into a frame) from a decoder
    // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga11e6542c4e66d3028668788a1a74217c
    response = avcodec_receive_frame(codecDetails->codecCtx, pFrame);
    if (response == AVERROR(EAGAIN) || response == AVERROR_EOF) {
      break;
    } else if (response < 0) {
      llog(ERROR, "Error while receiving a frame from the decoder: %s", av_err2str(response));
      returnCode = MAKE_AV_STAT(response);
      break;
    }

    if (response >= 0) {
      if (isEnabled(TRACE)) {
        llog(TRACE,
            "Frame %d (type=%c, size=%d bytes, format=%d) pts %d, key_frame %d [DTS %d]",
            codecDetails->codecCtx->frame_number,
            av_get_picture_type_char(pFrame->pict_type),
            pFrame->pkt_size,
            (AVPixelFormat)pFrame->format,
            pFrame->best_effort_timestamp,
            pFrame->key_frame,
            pFrame->coded_picture_number
        );
      }

      int32_t isRgb;
      uint64_t mat = IMakerManager::createMatFromFrame(pFrame, &(codecDetails->colorCvrt), isRgb, codecDetails->lastFormatUsed);

      returnCode = (*callback)(mat, isRgb, pPacket->stream_index);

      IMakerManager::freeImage(mat);
    }
  }

  // cleanup and return.
  av_frame_free(&pFrame);
  return returnCode;
}

extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_decodedFrameProcessor_create(push_frame pf) {
  DecodedFrameProcessor* ret = new DecodedFrameProcessor(pf);

  return (uint64_t)((MediaProcessor*)ret);
}

KAI_EXPORT void pcv4j_ffmpeg2_decodedFrameProcessor_replace(uint64_t native, push_frame pf) {
  DecodedFrameProcessor* ths = dynamic_cast<DecodedFrameProcessor*>((MediaProcessor*)native);
  ths->replace(pf);
}

}

}
} /* namespace pilecv4j */

