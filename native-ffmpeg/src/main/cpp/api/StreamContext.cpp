/*
 * StreamContext.cpp
 *
 *  Created on: Jul 14, 2022
 *      Author: jim
 */

#include "api/StreamContext.h"
#include "api/StreamDetails.h"

#include "kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "STRC"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

//========================================================================
// Local function defs.
//========================================================================
static uint64_t process_frames(StreamContext* ctx);
//========================================================================

// ===========================================================
// Stream Context methods
// ===========================================================

uint64_t StreamContext::open() {

  advanceStateTo(FRESH);

  if (state != FRESH) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)FRESH, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (!isSourceSet())
    return MAKE_P_STAT(NO_SOURCE_SET);

  uint64_t rc = 0;
  llog(DEBUG, "Opening stream source: %s", mediaDataSource->toString().c_str());

  // This should be impossible given the state management but if something odd happens
  // and the header has already been loaded then we can't open another (or the same) stream
  if (formatCtx) // if the formatCtx isn't null then this can't be in the FRESH state ... but belt-n-suspenders and all
    return MAKE_P_STAT(STREAM_IN_USE);

  formatCtx = avformat_alloc_context();

  {
    AVDictionary* opts = nullptr;
    buildOptions(&opts);
    rc = mediaDataSource->open(formatCtx, &opts);
    if (opts != nullptr)
      av_dict_free(&opts);
  }

  if (!isError(rc))
    state = OPEN;
  else // if open returned an error then it's required to free the AVFormatContext
    formatCtx = nullptr;
  // =====================================================

  return rc;
}

uint64_t StreamContext::load() {
  advanceStateTo(OPEN);

  if (state != OPEN) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)OPEN, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  // read's the stream data into the format
  uint64_t stat = MAKE_AV_STAT(avformat_find_stream_info(formatCtx, nullptr));
  if (isError(stat))
    return stat;

  // get the media types per input stream.
  {
    const int numStreams = formatCtx->nb_streams;

    if (numStreams > 0) {
      streamTypes = new AVMediaType[numStreams];
      for (int i = 0; i < numStreams; i++) {
        const AVStream* stream = formatCtx->streams ? formatCtx->streams[i] : nullptr;
        const AVCodecParameters *pLocalCodecParameters = stream == nullptr ? nullptr : stream->codecpar;
        streamTypes[i] = pLocalCodecParameters ? pLocalCodecParameters->codec_type : AVMEDIA_TYPE_UNKNOWN;
      }
    }
  }

  state = LOADED;

  return 0;
}


uint64_t StreamContext::getStreamDetails(StreamDetails** ppdetails, int* nb) {
  if (state < LOADED) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been AT LEAST %d but it's in %d.", (int)LOADED, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (state > PLAYING) {
    llog(ERROR, "StreamContext is in the wrong state. It can't be higher than %d but it's in %d.", (int)PLAYING, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  return StreamDetails::fillStreamDetails(formatCtx, ppdetails, nb);
}

uint64_t StreamContext::setupProcessors() {
  advanceStateTo(LOADED);

  if (state != LOADED) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)LOADED, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (mediaProcessors.size() == 0)
    return MAKE_P_STAT(NO_PROCESSOR_SET);

  uint64_t rc = 0;
  for (auto o : mediaProcessors) {
    rc = o->setup(formatCtx, options, nullptr);
    if (isError(rc))
      return rc;
  }

  state = PROCESSORS_SETUP;
  return 0;
}

uint64_t StreamContext::advanceStateTo(StreamContextState toAdvanceTo) {
  if (toAdvanceTo < state) {
    llog(ERROR, "StreamContext is in the wrong state. Cannot advance to %d when it's already in %d.", (int)toAdvanceTo, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (toAdvanceTo == state)
    return 0;

  uint64_t rc = 0;

  // =====================================================
  // OPEN
  // =====================================================
  if (state == FRESH) {
    rc = open();

    if (isError(rc))
      return rc;

    if (state != OPEN) {
      llog(ERROR, "Open did not result in an OPEN state.");
      return MAKE_P_STAT(STREAM_BAD_STATE);
    }

    if (toAdvanceTo == state)
      return 0;
  }
  // =====================================================

  // =====================================================
  // LOAD
  // =====================================================
  if (state == OPEN) {
    rc = load();

    if (isError(rc))
      return rc;

    if (state != LOADED) {
      llog(ERROR, "Load did not result in a LOADED state.");
      return MAKE_P_STAT(STREAM_BAD_STATE);
    }

    if (toAdvanceTo == state)
      return 0;
  }
  // =====================================================

  // =====================================================
  // PROCESSORS_SETUP
  // =====================================================
  if (state == LOADED) {
    rc = setupProcessors();

    if (isError(rc))
      return rc;

    if (state != PROCESSORS_SETUP) {
      llog(ERROR, "setupProcessors did not result in a PROCESSORS_SETUP state.");
      return MAKE_P_STAT(STREAM_BAD_STATE);
    }

    if (toAdvanceTo == state)
      return 0;
  }
  // =====================================================

  llog(ERROR, "Cannot use advanceState to advance beyond PROCESSORS_SETUP (%d)", (int)PROCESSORS_SETUP);
  return MAKE_P_STAT(STREAM_BAD_STATE);
}

uint64_t StreamContext::play() {

  uint64_t rc = 0;

  rc = advanceStateTo(PROCESSORS_SETUP);
  if (isError(rc))
    return rc;

  rc  = process_frames(this);
  state = ENDED;

  if (isError(rc))
    return rc;

  return 0;
}

uint64_t StreamContext::stop() {
  if (isEnabled(TRACE))
    llog(TRACE,"Stopping with a current state of %d", (int) state);
  if (state >= STOPPING)
    return 0;
  if (state != PLAYING) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)PLAYING, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }
  state = STOPPING;
  stopMe = true;
  return 0;
}

extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_create() {
  uint64_t ret = (uint64_t) new StreamContext();
  if (isEnabled(TRACE))
    llog(TRACE, "Creating new StreamContext: %" PRId64, ret);
  return ret;
}

KAI_EXPORT void pcv4j_ffmpeg2_streamContext_delete(uint64_t ctx) {
  if (isEnabled(TRACE))
    llog(TRACE, "Deleting StreamContext: %" PRId64, ctx);
  StreamContext* c = (StreamContext*)ctx;
  delete c;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_setSource(uint64_t ctx, uint64_t mediaDataSource) {
  StreamContext* c = (StreamContext*)ctx;
  MediaDataSource* vds = (MediaDataSource*)mediaDataSource;
  llog(DEBUG, "Setting source to %s", vds == nullptr ? "null" : PO(vds->toString().c_str()) );
  return c->setSource((MediaDataSource*)mediaDataSource);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_addProcessor(uint64_t ctx, uint64_t mediaProcessor) {
  StreamContext* c = (StreamContext*)ctx;
  MediaProcessor* vds = (MediaProcessor*)mediaProcessor;
  llog(DEBUG, "Adding processor at %" PRId64 " to StreamContext at %" PRId64, mediaProcessor, ctx);
  return c->addProcessor((MediaProcessor*)vds);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_addOption(uint64_t ctx, const char* key, const char* value) {
  StreamContext* c = (StreamContext*)ctx;
  llog(INFO, "Setting option \"%s\" = \"%s\"",key,value);
  return c->addOption(key, value);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_stop(uint64_t ctx){
  StreamContext* c = (StreamContext*)ctx;
  if (isEnabled(TRACE))
    llog(TRACE,"Stopping with a current state of %d", (int) c->state);
  if (c->state >= STOPPING)
    return 0;
  if (c->state != PLAYING) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)PLAYING, (int)c->state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }
  c->state = STOPPING;
  c->stopMe = true;
  return 0;
}

KAI_EXPORT void pcv4j_ffmpeg2_streamContext_sync(uint64_t ctx){
  StreamContext* c = (StreamContext*)ctx;
  if (isEnabled(DEBUG))
    llog(DEBUG,"Setting stream %" PRId64 " to synchronous.", ctx);

  c->sync();
}

KAI_EXPORT uint32_t pcv4j_ffmpeg2_streamContext_state(uint64_t ctx) {
  StreamContext* c = (StreamContext*)ctx;
  return (int32_t) c->state;
}

KAI_EXPORT StreamDetails* pcv4j_ffmpeg2_streamContext_getStreamDetails(uint64_t ctx, int32_t* numResults, uint64_t* rc) {
  StreamDetails* ret = nullptr;
  StreamContext* c = (StreamContext*)ctx;
  *rc = c->getStreamDetails(&ret, numResults);
  return ret;
}

KAI_EXPORT void pcv4j_ffmpeg2_streamDetails_deleteArray(void* sdRef) {
  StreamDetails* c = (StreamDetails*)sdRef;
  delete [] c;
}

// ===========================================================
// Stream Context lifecycle
// ===========================================================

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_play(uint64_t ctx) {
  StreamContext* c = (StreamContext*)ctx;
  return c->play();
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_load(uint64_t ctx) {
  StreamContext* c = (StreamContext*)ctx;
  return c->load();
}

}

static uint64_t process_frames(StreamContext* c) {
  int remuxErrorCount = 0; // if this count goes above MAX_REMUX_ERRORS then process_frames will fail.

  if (c->state != PROCESSORS_SETUP) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)PROCESSORS_SETUP, (int)c->state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  Synchronizer* throttle = c->throttle;

  c->state = PLAYING;

  // https://ffmpeg.org/doxygen/trunk/structAVPacket.html
  AVPacket *pPacket = av_packet_alloc();
  if (!pPacket)
  {
    llog(ERROR, "failed to allocate memory for AVPacket");
    return MAKE_P_STAT(FAILED_CREATE_PACKET);
  }

  AVFormatContext* pFormatContext = c->formatCtx;

  int av_rc = 0;
  uint64_t rc = 0;

  for (auto o : c->mediaProcessors) {
    uint64_t frc = 0;
    frc = o->preFirstFrame(pFormatContext);
    if (isError(frc))
      goto end;
  }

  if (throttle)
    throttle->start();

  // fill the Packet with data from the Stream
  // https://ffmpeg.org/doxygen/trunk/group__lavf__decoding.html#ga4fdb3084415a82e3810de6ee60e46a61
  while ((av_rc = av_read_frame(pFormatContext, pPacket)) >= 0 && !c->stopMe)
  {
    const int streamIndex = (int)pPacket->stream_index;
    llog(TRACE, "AVPacket->stream_index,pts,dts %d %" PRId64 ",%" PRId64, streamIndex, pPacket->pts, pPacket->dts);

    if (!throttle || !throttle->throttle(pFormatContext, pPacket)) {
      for (auto o : c->mediaProcessors) {
        rc = o->handlePacket(pFormatContext, pPacket, c->streamTypes[streamIndex]);

        if (isError(rc))
          break;
      }
    }

    // https://ffmpeg.org/doxygen/trunk/group__lavc__packet.html#ga63d5a489b419bd5d45cfd09091cbcbc2
    av_packet_unref(pPacket);

    if (av_rc < 0 || isError(rc))
      break;
  }

  if (av_rc < 0)
    llog(INFO, "Last result of read was: %s", av_err2str(av_rc));

  end:
  if (pPacket)
    av_packet_free(&pPacket);

  return av_rc < 0 ? MAKE_AV_STAT(av_rc) : MAKE_P_STAT(rc);
}
}

} /* namespace pilecv4j */
