/*
 * MediaOutput.cpp
 *
 *  Created on: Aug 9, 2022
 *      Author: jim
 */

#include <api/Muxer.h>
#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"
#include "common/kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{
#define COMPONENT "MUXR"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

uint64_t Muxer::createStreamFromCodecParams(AVFormatContext* output_format_context, AVCodecParameters* in_codecpar, AVStream** out) {
  AVStream* out_stream = avformat_new_stream(output_format_context, nullptr);
  if (!out_stream) {
    log(ERROR, COMPONENT, "Failed allocating output stream");
    if (out)
      *out = nullptr;
    return MAKE_AV_STAT(AVERROR_UNKNOWN);
  }

  uint64_t iret = MAKE_AV_STAT(avcodec_parameters_copy(out_stream->codecpar, in_codecpar));
  if (isError(iret)) {
    log(ERROR, COMPONENT, "Failed to copy codec parameters");
    if (out)
      *out = nullptr;
    return iret;
  }

  if (out)
    *out = out_stream;
  return 0;
}

uint64_t Muxer::createStreamFromCodec(AVFormatContext* output_format_context, const AVCodecContext* codecc, AVStream** out) {
  const AVCodec* codec = codecc->codec;
  if (!codec) {
    log(ERROR, COMPONENT, "The AVCodecContext at %" PRId64 " doesn't have the codec set.", (uint64_t)codecc);
    return MAKE_P_STAT(NO_SUPPORTED_CODEC);
  }

  AVStream* out_stream = avformat_new_stream(output_format_context, codec);
  if (!out_stream) {
    log(ERROR, COMPONENT, "Failed allocating output stream");
    if (out)
      *out = nullptr;
    return MAKE_AV_STAT(AVERROR_UNKNOWN);
  }
  if (out)
    *out = out_stream;
  return 0;
}

uint64_t Muxer::writePacket(const AVPacket* inPacket, const AVRational& time_base, int output_stream_index) {
  AVFormatContext* output_format_context = getFormatContext();
  AVStream* out_stream = output_format_context->streams[output_stream_index];

  if (isEnabled(DEBUG))
    logPacket(DEBUG, COMPONENT, "Prescaled Packet", inPacket, time_base);

  AVPacket* pPacket = av_packet_clone(inPacket);
  if (!pPacket) {
    log(ERROR, COMPONENT, "Failed to clone a packet");
    return MAKE_AV_STAT(AVERROR_UNKNOWN);
  }

  pPacket->stream_index = output_stream_index;
  // https://ffmpeg.org/doxygen/trunk/structAVPacket.html#ab5793d8195cf4789dfb3913b7a693903
  pPacket->pos = -1;

  // ======================================================================================
  // adjust the packet timing
  if (pPacket->pts == AV_NOPTS_VALUE) {
    if (!loggedPacketPtsDtsMissingAlready) {
      log(WARN, COMPONENT, "Packet has no pts/dts set. It will be sent as is to the output");
      loggedPacketPtsDtsMissingAlready = true;
    }
  } else {
//    if (isEnabled(TRACE))
//      log(TRACE, COMPONENT, "in tb = %d/%d, out = %d/%d", time_base.num, time_base.den, out_stream->time_base.num, out_stream->time_base.den);
    if (pPacket->dts == AV_NOPTS_VALUE)
      pPacket->dts = pPacket->pts;

    pPacket->pts = av_rescale_q_rnd(pPacket->pts, time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
    pPacket->dts = av_rescale_q_rnd(pPacket->dts, time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
    if (!starting_ts_offset) {
      // we need to set up the stream starting offset lookup.
      nb_streams = output_format_context->nb_streams;
      starting_ts_offset = new int64_t[nb_streams];
      for (int i = 0; i < nb_streams; i++)
        starting_ts_offset[i] = AV_NOPTS_VALUE;
    }
    if (starting_ts_offset[output_stream_index] == AV_NOPTS_VALUE) {
       log(INFO, COMPONENT, "starting Muxer. Saving off stream %d starting offset as %" PRId64, (int)output_stream_index, (int64_t)pPacket->pts);
       starting_ts_offset[output_stream_index] = pPacket->pts;
    }
    const int64_t offset = starting_ts_offset[output_stream_index];
    if (isEnabled(DEBUG))
      log(DEBUG, COMPONENT, "starting ts offset = %ld", (long)offset);
    pPacket->pts -= offset;
    pPacket->dts -= offset;
    if (isEnabled(DEBUG))
      logPacket(DEBUG, COMPONENT, "Shifted Packet", pPacket, output_format_context);

    pPacket->duration = av_rescale_q(pPacket->duration, time_base, out_stream->time_base);
  }
  // ======================================================================================

  if (isEnabled(DEBUG))
    logPacket(DEBUG, COMPONENT, "Rescaled Packet", pPacket, output_format_context);

  int ret = writeFinalPacket(pPacket);
  av_packet_free(&pPacket);
  if (ret < 0)
    log(ERROR, COMPONENT, "Error muxing packet \"%s\"", av_err2str(ret));
  return MAKE_AV_STAT(ret);
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

KAI_EXPORT void pcv4j_ffmpeg2_muxer_delete(uint64_t outputRef) {
  PILECV4J_TRACE;

  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "destroying muxer %" PRId64, outputRef);

  if (outputRef) {
    Muxer* output = (Muxer*)outputRef;
    output->close();
    delete output;
  }
}

}
}
} /* namespace pilecv4j */
