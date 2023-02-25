/*
 * DecodedFrameProcessor.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "api/PacketSourceInfo.h"
#include "processors/DecodedFrameProcessor.h"

#include "utils/IMakerManager.h"
#include "utils/log.h"

#include "common/kog_exports.h"
#include "utils/timing.h"

extern "C" {
#include <libavutil/opt.h>
}

#include <chrono>
#include <thread>

namespace pilecv4j
{
namespace ffmpeg
{

TIME_DECL(decode);
TIME_DECL(decode_and_handle);
TIME_DECL(create_mat);
TIME_DECL(handle);

#define COMPONENT "DEFP"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

const std::string PREFER_BGR = "pilecv4j:prefer_bgr";

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

  int dstW = -1;
  int dstH = -1;

  inline void close() {
    if (colorCvrt != nullptr)
      sws_freeContext(colorCvrt);
    if (codecCtx != nullptr)
      avcodec_free_context(&codecCtx);
  }

  inline ~CodecDetails() = default;
};

uint64_t DecodedFrameProcessor::close() {
  PILECV4J_TRACE;
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

uint64_t DecodedFrameProcessor::setup(PacketSourceInfo* psi, std::vector<std::tuple<std::string,std::string> >& options) {
  PILECV4J_TRACE;
  if (!psi)
    return MAKE_P_STAT(NO_PACKET_SOURCE_INFO);

  uint64_t ret = 0;
  int numStreams;
  if (isError(ret = psi->numStreams(&numStreams)))
    return ret;

  if (numStreams <= 0)
    return MAKE_P_STAT(NO_STREAM);

  //llog(TRACE,"HERE1");
  std::string preferBgrStr = removeOption(PREFER_BGR, options);
  //llog(TRACE,"HERE2");

  if (preferBgrStr == "1" || preferBgrStr == "true" || preferBgrStr == "TRUE")
    requestedPixFormat = AV_PIX_FMT_BGR24;

  codecs = new CodecDetails*[numStreams];
  for (int i = 0; i < numStreams; i++) {
    codecs[i] = nullptr;

    AVStream* lStream;
    if (isError(ret = psi->getStream(i, &lStream)))
      return ret;

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
    uint64_t rc = MediaProcessor::open_codec(lStream,&opts,&(codecs[i]->codecCtx), decoderNameSet ? decoderName.c_str() : nullptr);
    if (isEnabled(INFO) && options.size() > 0 && !isError(rc)) {
      rebuildOptions(opts, options);
      std::string header = "after opening the decoder for stream ";
      header += std::to_string(i);
      logRemainingOptions(INFO, COMPONENT, header.c_str(), options);
    }
    if (opts != nullptr)
      av_dict_free(&opts);
    if (isError(rc))
      return rc;
  }

  return 0;
}

uint64_t DecodedFrameProcessor::handlePacket(AVPacket* pPacket, AVMediaType mediaType) {
  PILECV4J_TRACE;
  if (codecs == nullptr) {
    llog(ERROR, "handle packet called on uninitialized DecodedFrameProcessor");
    return MAKE_P_STAT(NO_SUPPORTED_CODEC);
  }
  const int stream_index = pPacket->stream_index;
  if (codecs[stream_index])
    return decode_packet(codecs[pPacket->stream_index], pPacket);
  else {
    if (isEnabled(TRACE))
      llog(TRACE, "packet passed with no corresponding codec.");
    return 0;
  }
}

uint64_t DecodedFrameProcessor::decode_packet(CodecDetails* codecDetails, AVPacket *pPacket) {
  PILECV4J_TRACE;
  TIME_GUARD(decode_and_handle);

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
  TIME_OPEN(decode);
  int response = avcodec_send_packet(codecDetails->codecCtx, pPacket);

  if (response < 0 && response != AVERROR_INVALIDDATA) {
    TIME_CAP(decode);
    llog(ERROR, "Error while sending a packet to the decoder: %s", av_err2str(response));
    return MAKE_AV_STAT(response);
  }

  // https://ffmpeg.org/doxygen/trunk/structAVFrame.html
  AVFrame *pFrame = av_frame_alloc();
  if (!pFrame)
  {
    TIME_CAP(decode);
    llog(ERROR, "failed to allocated memory for AVFrame");
    return MAKE_P_STAT(FAILED_CREATE_FRAME);
  }

  uint64_t returnCode = 0;
  while (response >= 0 && returnCode == 0)
  {
    // Return decoded output data (into a frame) from a decoder
    // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga11e6542c4e66d3028668788a1a74217c
    response = avcodec_receive_frame(codecDetails->codecCtx, pFrame);
    TIME_CAP(decode);
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
      TIME_OPEN(create_mat);
      uint64_t mat = IMakerManager::createMatFromFrame(pFrame, maxDim, &(codecDetails->colorCvrt), isRgb,
          codecDetails->lastFormatUsed, codecDetails->dstW, codecDetails->dstH, requestedPixFormat);
      TIME_CAP(create_mat);

      TIME_OPEN(handle);
      // management of the mat is passed to the
      returnCode = (*callback)(mat, isRgb, pPacket->stream_index);
      TIME_CAP(handle);

      IMakerManager::freeImage(mat);
    }
  }

  // cleanup and return.
  av_frame_free(&pFrame);
  return returnCode;
}

extern void displayImageMakerTimings();

void displayDecodeTiming() {
  TIME_DISPLAY("decoding and handing", decode_and_handle);
  TIME_DISPLAY("decoding", decode);
  TIME_DISPLAY("creating cv::Mat", create_mat);
  displayImageMakerTimings();
  TIME_DISPLAY("handling in java", handle);
}

extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_decodedFrameProcessor_create(push_frame pf, int32_t maxDim, const char* decoderName) {
  DecodedFrameProcessor* ret = new DecodedFrameProcessor(pf, (int)maxDim, decoderName);

  llog(INFO, "Creating DecodedFrameProcessor with maxDim %d", (int)maxDim);

  return (uint64_t)((MediaProcessor*)ret);
}

KAI_EXPORT void pcv4j_ffmpeg2_decodedFrameProcessor_replace(uint64_t native, push_frame pf) {
  DecodedFrameProcessor* ths = dynamic_cast<DecodedFrameProcessor*>((MediaProcessor*)native);
  ths->replace(pf);
}

}

}
} /* namespace pilecv4j */

