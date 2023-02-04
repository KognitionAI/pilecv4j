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

uint64_t Muxer::createStreamFromCodec(AVFormatContext* output_format_context, AVCodec* codec, AVStream** out) {
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
