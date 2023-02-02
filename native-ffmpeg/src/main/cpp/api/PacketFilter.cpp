/*
 * PacketFilter.cpp
 *
 *  Created on: Aug 16, 2022
 *      Author: jim
 */

#include "api/PacketFilter.h"
#include "api/PacketSourceInfo.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "common/kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{

static AVRational nullRat{ -1, -1 };

#define COMPONENT "PACF"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

uint64_t PacketFilter::calculateTimeBaseReference(PacketSourceInfo* psi, AVRational** time_bases, int* numStreams) {
  PILECV4J_TRACE;
  if (!psi)
    return MAKE_P_STAT(NO_PACKET_SOURCE_INFO);

  if (!numStreams) {
    llog(ERROR, "NULL parameter 'numStreams'");
    return MAKE_P_STAT(NULL_PARAMETER);
  }

  if (!time_bases) {
    llog(ERROR, "NULL parameter 'time_bases'");
    return MAKE_P_STAT(NULL_PARAMETER);
  }

  *numStreams = 0;
  *time_bases = nullptr;

  uint64_t iret = 0;

  int number_of_streams = 0;
  if (isError(iret = psi->numStreams(&number_of_streams)))
    return iret; // no need to goto fail since we haven't allocated anything yet.

  *numStreams = number_of_streams;
  AVRational* streamTimeBases = new AVRational[number_of_streams];

  for (unsigned int i = 0; i < number_of_streams; i++) {
    AVStream* in_stream;
    if (isError(iret = psi->getStream(i, &in_stream))) {
      goto fail;
    }

    streamTimeBases[i] = in_stream ? in_stream->time_base : nullRat;
  }

  *numStreams = number_of_streams;
  *time_bases = streamTimeBases;
  return 0;

  fail:
  if (streamTimeBases)
    delete[] streamTimeBases;
  return iret;
}

extern "C" {
KAI_EXPORT void pcv4j_ffmpeg2_packetFilter_destroy(uint64_t nativeRef) {
  if (isEnabled(TRACE))
    llog(TRACE, "destroying packet filter %" PRId64, nativeRef);

  PacketFilter* packetFilter = (PacketFilter*)(nativeRef);
  if (packetFilter)
    delete packetFilter;
}
}

}
} /* namespace pilecv4j */
