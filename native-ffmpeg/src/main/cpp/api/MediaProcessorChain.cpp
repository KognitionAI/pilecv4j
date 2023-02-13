/*
 * MediaProcessorChain.cpp
 *
 *  Created on: Jul 13, 2022
 *      Author: jim
 */

#include "api/MediaProcessorChain.h"
#include "api/PacketFilter.h"

#include "common/kog_exports.h"
#include "utils/log.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "MPCH"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

MediaProcessorChain::~MediaProcessorChain() {
}

uint64_t MediaProcessorChain::setup(PacketSourceInfo* psi, std::vector<std::tuple<std::string,std::string> >& options) {
  if (!psi)
    return MAKE_P_STAT(NO_PACKET_SOURCE_INFO);

  for (auto o : packetFilters) {
    uint64_t rc = o->setup(psi, options);
    if (isError(rc))
      return rc;
  }

  for (auto o : mediaProcessors) {
    uint64_t rc = o->setup(psi, options);
    if (isError(rc))
      return rc;
  }
  return 0;
}

uint64_t MediaProcessorChain::preFirstFrame() {
  for (auto o : mediaProcessors) {
    uint64_t rc = o->preFirstFrame();
    if (isError(rc))
      return rc;
  }
  return 0;
}

uint64_t MediaProcessorChain::handlePacket(AVPacket* pPacket, AVMediaType streamMediaType) {
  for (auto f : packetFilters) {
    if (!f->filter(pPacket, streamMediaType))
      return 0L;
  }

  for (auto o : mediaProcessors) {
    uint64_t rc = o->handlePacket(pPacket, streamMediaType);
    if (isError(rc))
      return rc;
  }
  return 0;
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_mediaProcessorChain_create() {
  MediaProcessorChain* ret = new MediaProcessorChain();
  return (uint64_t)ret;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_mediaProcessorChain_addProcessor(uint64_t mpc, uint64_t mediaProcessor) {
  MediaProcessorChain* c = (MediaProcessorChain*)mpc;
  MediaProcessor* vds = (MediaProcessor*)mediaProcessor;
  llog(DEBUG, "Adding processor at %" PRId64 " to media processor chain at %" PRId64, mediaProcessor, mpc);
  return c->addProcessor((MediaProcessor*)vds);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_mediaProcessorChain_addPacketFilter(uint64_t mpc, uint64_t packetFilter) {
  MediaProcessorChain* c = (MediaProcessorChain*)mpc;
  PacketFilter* vds = (PacketFilter*)packetFilter;
  llog(DEBUG, "Adding packet filter at %" PRId64 " to media processor chain at %" PRId64, packetFilter, mpc);
  return c->addPacketFilter((PacketFilter*)vds);
}

KAI_EXPORT void pcv4j_ffmpeg2_mediaProcessorChain_destroy(uint64_t mpcRef) {
  if (isEnabled(TRACE))
    llog(TRACE, "destroying processor chain %" PRId64, mpcRef);

  MediaProcessorChain* ret = (MediaProcessorChain*)mpcRef;
  if (ret != nullptr)
    delete ret;
}

}

}
} /* namespace pilecv4j */

