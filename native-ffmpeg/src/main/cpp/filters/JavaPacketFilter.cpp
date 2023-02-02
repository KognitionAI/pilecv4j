/*
 * JavaStreamSelector.cpp
 *
 *  Created on: Jul 17, 2022
 *      Author: jim
 */

#include "JavaPacketFilter.h"

#include "utils/log.h"
#include "utils/pilecv4j_ffmpeg_utils.h"

#include "common/kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "JPKF"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

static AVRational nullRat{ -1, -1 };

bool JavaPacketFilter::filter(AVPacket* packet, AVMediaType streamMediaType) {

  const int stream_index = packet->stream_index;
  AVRational& tb = streamTimeBases[stream_index];

  return (*callback)(streamMediaType, stream_index, packet->size, (packet->flags & AV_PKT_FLAG_KEY) ? 1 : 0,
      packet->pts, packet->dts, tb.num, tb.den) ? true : false;
}

extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_javaPacketFilter_create(packet_filter callback) {

  uint64_t ret = (uint64_t) ((PacketFilter*)new JavaPacketFilter(callback));
  if (isEnabled(TRACE))
    llog(TRACE, "Creating new JavaPacketFilter: %" PRId64, ret);
  return ret;
}

}
} /* namespace pilecv4j */

}
