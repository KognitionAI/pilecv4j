/*
 * MediaProcessorChain.cpp
 *
 *  Created on: Jul 13, 2022
 *      Author: jim
 */

#include "api/MediaProcessorChain.h"

#include "kog_exports.h"
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
  if (selectedStreams)
    delete[] selectedStreams;
}

uint64_t MediaProcessorChain::setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* defaultSelectedStreams) {
  if (avformatCtx->streams == nullptr || avformatCtx->nb_streams <= 0)
    return MAKE_P_STAT(NO_STREAM);

  if (defaultSelectedStreams && selector)
    llog(WARN, "MediaProcessorChain was passed a non-null set of streams. They will be ignored and stream selection will be deferred to the given selector");

  streamCount = avformatCtx->nb_streams;
  selectedStreams = new bool[streamCount];

  for (int i = 0; i < streamCount; i++)
    selectedStreams[i] = true;

  if (selector)
    selector->selectStreams(avformatCtx, selectedStreams, streamCount);
  else if (defaultSelectedStreams) {
    for (int i = 0; i < streamCount; i++)
      selectedStreams[i] = defaultSelectedStreams[i];
  }

  for (auto o : mediaProcessors) {
    uint64_t rc = o->setup(avformatCtx, options, selectedStreams);
    if (isError(rc))
      return rc;
  }
  return 0;
}

uint64_t MediaProcessorChain::preFirstFrame(AVFormatContext* avformatCtx) {
  for (auto o : mediaProcessors) {
    uint64_t rc = o->preFirstFrame(avformatCtx);
    if (isError(rc))
      return rc;
  }
  return 0;
}

uint64_t MediaProcessorChain::handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType) {
  if (!streamSelected(selectedStreams, pPacket->stream_index))
    return 0L;

  for (auto o : mediaProcessors) {
    uint64_t rc = o->handlePacket(avformatCtx, pPacket, streamMediaType);
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

KAI_EXPORT uint64_t pcv4j_ffmpeg2_mediaProcessorChain_setStreamSelector(uint64_t mpc, uint64_t selector) {
  MediaProcessorChain* c = (MediaProcessorChain*)mpc;
  StreamSelector* vds = (StreamSelector*)selector;
  llog(DEBUG, "Adding stream selector at %" PRId64 " to media processor chain at %" PRId64, selector, mpc);
  return c->setStreamSelector((StreamSelector*)vds);
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

