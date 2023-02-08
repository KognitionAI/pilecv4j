/*
 * StreamContext.cpp
 *
 *  Created on: Jul 14, 2022
 *      Author: jim
 */

#include "api/StreamDetails.h"

#include "common/kog_exports.h"
#include "utils/timing.h"

#ifdef PILECV4J_FFMPEG_DEVICE_SUPPORT
extern "C" {
#include "libavdevice/avdevice.h"
}
#endif

#define _INSIDE_PILECV4J_FFMPEG_STREAMCONTEXT_CPP
#include "api/StreamContext.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "SCTX"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

TIME_DECL(read_frame);
TIME_DECL(hande_packet);
TIME_DECL(read_and_process_frame);
TIME_DECL(throttle);

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

//========================================================================
// Local function defs.
//========================================================================
static uint64_t process_packets(StreamContext* ctx);
//========================================================================

// ===========================================================
// Stream Context methods
// ===========================================================

uint64_t StreamContext::open() {
  PILECV4J_TRACE;
#ifdef PILECV4J_FFMPEG_DEVICE_SUPPORT
  avdevice_register_all();
#endif

  advanceStateTo(FRESH);

  if (state != FRESH) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)FRESH, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  if (!isSourceSet())
    return MAKE_P_STAT(NO_SOURCE_SET);

  uint64_t rc = 0;
  llog(DEBUG, "Opening stream source: %s", mediaDataSource->toString().c_str());

  // This should be impossible given the state management but if something odd happens
  // and the header has already been loaded then we can't open another (or the same) stream
  if (formatCtx) // if the formatCtx isn't null then this can't be in the FRESH state ... but belt-n-suspenders and all
    return MAKE_P_STAT(IN_USE);

  formatCtx = avformat_alloc_context();

  {
    AVDictionary* opts = nullptr;
    llog(TRACE, "number of options set in StreamContext::open: %d", (int)options.size());
    uint64_t iret = 0;
    if (isError(iret = buildOptions(options, &opts))) {
      llog(ERROR, "Failed setting build options: %" PRId64 ", %s", iret, errMessage(iret));
      return iret;
    }
    rc = mediaDataSource->open(&formatCtx, &opts);
    if (opts != nullptr)
      av_dict_free(&opts);
  }

  if (!isError(rc))
    state = OPEN;
  else { // if open returned an error then it's required to free the AVFormatContext
    if (formatCtx)
      avformat_free_context(formatCtx);
    formatCtx = nullptr;
  }
  // =====================================================

  return rc;
}

uint64_t StreamContext::load() {
  PILECV4J_TRACE;
  advanceStateTo(OPEN);

  if (state != OPEN) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)OPEN, (int)state);
    return MAKE_P_STAT(BAD_STATE);
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
  PILECV4J_TRACE;
  if (state < LOADED) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been AT LEAST %d but it's in %d.", (int)LOADED, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  if (state > PLAYING) {
    llog(ERROR, "StreamContext is in the wrong state. It can't be higher than %d but it's in %d.", (int)PLAYING, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  return StreamDetails::fillStreamDetails(formatCtx, ppdetails, nb);
}

uint64_t StreamContext::setupProcessors() {
  PILECV4J_TRACE;
  advanceStateTo(LOADED);

  if (state != LOADED) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)LOADED, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  if (mediaProcessors.size() == 0)
    return MAKE_P_STAT(NO_PROCESSOR_SET);

  uint64_t rc = 0;
  for (auto o : mediaProcessors) {
    rc = o->setup(this, options);
    if (isError(rc))
      return rc;
  }

  state = PROCESSORS_SETUP;
  return 0;
}

uint64_t StreamContext::getStream(int streamIndex, AVStream** streamOut) {
  PILECV4J_TRACE;
  if (!streamOut) {
    llog(ERROR, "NULL parameter 'streamOut'");
    return MAKE_P_STAT(NULL_PARAMETER);
  }

  uint64_t ret = 0;
  if (state < OPEN) {
    if (isError(ret = advanceStateTo(OPEN)))
        return ret;
  }

  // formatCtx MUST be set or the advanceStateTo(OPEN) would have failed.

  if (streamIndex >= formatCtx->nb_streams) {
    llog(ERROR, "There is not stream at index %d. The total number of streams is %d", (int)streamIndex, (int)formatCtx->nb_streams);
    return MAKE_P_STAT(NO_STREAM);
  }

  *streamOut = formatCtx->streams[streamIndex];
  return 0;
}

uint64_t StreamContext::numStreams(int* numStreamsOut) {
  PILECV4J_TRACE;
  if (!numStreamsOut) {
    llog(ERROR, "NULL parameter 'numStreamsOut'");
    return MAKE_P_STAT(NULL_PARAMETER);
  }

  uint64_t ret = 0;
  if (state < OPEN) {
    if (isError(ret = advanceStateTo(OPEN)))
        return ret;
  }

  *numStreamsOut = formatCtx->nb_streams;
  return 0;
}

uint64_t StreamContext::getCodecTag(AVCodecID codecId, unsigned int* tagOut) {
  PILECV4J_TRACE;
  if (!tagOut) {
    llog(ERROR, "NULL parameter 'tagOut'");
    return MAKE_P_STAT(NULL_PARAMETER);
  }

  uint64_t ret = 0;
  if (state < OPEN) {
    if (isError(ret = advanceStateTo(OPEN)))
        return ret;
  }

  av_codec_get_tag2(formatCtx->iformat->codec_tag, codecId, tagOut);
  return 0;
}

uint64_t StreamContext::advanceStateTo(StreamContextState toAdvanceTo) {
  PILECV4J_TRACE;
  if (toAdvanceTo < state) {
    llog(ERROR, "StreamContext is in the wrong state. Cannot advance to %d when it's already in %d.", (int)toAdvanceTo, (int)state);
    return MAKE_P_STAT(BAD_STATE);
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
      return MAKE_P_STAT(BAD_STATE);
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
      return MAKE_P_STAT(BAD_STATE);
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
      return MAKE_P_STAT(BAD_STATE);
    }

    if (toAdvanceTo == state)
      return 0;
  }
  // =====================================================

  llog(ERROR, "Cannot use advanceState to advance beyond PROCESSORS_SETUP (%d)", (int)PROCESSORS_SETUP);
  return MAKE_P_STAT(BAD_STATE);
}

uint64_t StreamContext::play() {
  PILECV4J_TRACE;

  uint64_t rc = 0;

  rc = advanceStateTo(PROCESSORS_SETUP);
  if (isError(rc))
    return rc;

  rc  = process_packets(this);
  state = ENDED;

  if (isError(rc))
    return rc;

  return 0;
}

uint64_t StreamContext::stop() {
  PILECV4J_TRACE;

  if (isEnabled(TRACE))
    llog(TRACE,"Stopping with a current state of %d", (int) state);
  if (state >= STOPPING)
    return 0;
  if (state != PLAYING) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)PLAYING, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }
  state = STOPPING;
  stopMe = true;
  return 0;
}

extern void displayDecodeTiming();
#ifdef TIMING
static void displayDecoderTimings() {
  TIME_DISPLAY("Overall reading and processing frame packets", read_and_process_frame);
  TIME_DISPLAY("reading frame/packet", read_frame);
  TIME_DISPLAY("handling/decoding/remuxing", hande_packet);
  displayDecodeTiming();
  TIME_DISPLAY("waiting due to synchronization", throttle);
}
#endif

extern "C" {

KAI_EXPORT void pcv4j_ffmpeg2_timings() {
#ifdef TIMING
  displayDecoderTimings();
#endif
}


KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_create() {
  PILECV4J_TRACE;
  uint64_t ret = (uint64_t) new StreamContext();
  if (isEnabled(TRACE))
    llog(TRACE, "Creating new StreamContext: %" PRId64, ret);
  return ret;
}

KAI_EXPORT void pcv4j_ffmpeg2_streamContext_delete(uint64_t ctx) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Deleting StreamContext: %" PRId64, ctx);
  StreamContext* c = (StreamContext*)ctx;
  delete c;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_setSource(uint64_t ctx, uint64_t mediaDataSource) {
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  MediaDataSource* vds = (MediaDataSource*)mediaDataSource;
  llog(DEBUG, "Setting source to %s", vds == nullptr ? "null" : PO(vds->toString().c_str()) );
  return c->setSource((MediaDataSource*)mediaDataSource);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_addProcessor(uint64_t ctx, uint64_t mediaProcessor) {
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  MediaProcessor* vds = (MediaProcessor*)mediaProcessor;
  llog(DEBUG, "Adding processor at %" PRId64 " to StreamContext at %" PRId64, mediaProcessor, ctx);
  return c->addProcessor((MediaProcessor*)vds);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_addOption(uint64_t ctx, const char* key, const char* value) {
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  llog(INFO, "Setting option \"%s\" = \"%s\"",key,value);
  return c->addOption(key, value);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_stop(uint64_t ctx){
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  if (isEnabled(TRACE))
    llog(TRACE,"Stopping with a current state of %d", (int) c->state);
  if (c->state >= STOPPING)
    return 0;
  if (c->state != PLAYING) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)PLAYING, (int)c->state);
    return MAKE_P_STAT(BAD_STATE);
  }
  c->state = STOPPING;
  c->stopMe = true;
  return 0;
}

KAI_EXPORT void pcv4j_ffmpeg2_streamContext_sync(uint64_t ctx){
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  if (isEnabled(DEBUG))
    llog(DEBUG,"Setting stream %" PRId64 " to synchronous.", ctx);

  c->sync();
}

KAI_EXPORT uint32_t pcv4j_ffmpeg2_streamContext_state(uint64_t ctx) {
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  return (int32_t) c->state;
}

KAI_EXPORT StreamDetails* pcv4j_ffmpeg2_streamContext_getStreamDetails(uint64_t ctx, int32_t* numResults, uint64_t* rc) {
  PILECV4J_TRACE;
  StreamDetails* ret = nullptr;
  StreamContext* c = (StreamContext*)ctx;
  *rc = c->getStreamDetails(&ret, numResults);
  return ret;
}

KAI_EXPORT void pcv4j_ffmpeg2_streamDetails_deleteArray(void* sdRef) {
  PILECV4J_TRACE;
  StreamDetails* c = (StreamDetails*)sdRef;
  delete [] c;
}

// ===========================================================
// Stream Context lifecycle
// ===========================================================

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_play(uint64_t ctx) {
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  return c->play();
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_streamContext_load(uint64_t ctx) {
  PILECV4J_TRACE;
  StreamContext* c = (StreamContext*)ctx;
  return c->load();
}

}

#ifdef TIMING
static inline int read_frame(AVFormatContext* pFormatContext, AVPacket * pPacket) {
  TIME_GUARD(read_frame);
  return av_read_frame(pFormatContext, pPacket);
}

static inline bool dontSkipPacket(Synchronizer* throttle, const AVFormatContext* pFormatContext, const AVPacket * pPacket) {
  TIME_GUARD(throttle);
  return !throttle || !throttle->throttle(pFormatContext, pPacket);
}
#else
#define read_frame av_read_frame
#define dontSkipPacket(t,f,p) !t || !t->throttle(f, p)
#endif

static uint64_t process_packets(StreamContext* c) {
  PILECV4J_TRACE;
  int remuxErrorCount = 0; // if this count goes above MAX_REMUX_ERRORS then process_frames will fail.

  if (c->state != PROCESSORS_SETUP) {
    llog(ERROR, "StreamContext is in the wrong state. It should have been in %d but it's in %d.", (int)PROCESSORS_SETUP, (int)c->state);
    return MAKE_P_STAT(BAD_STATE);
  }

  Synchronizer* throttle = c->throttle;

  c->state = PLAYING;

  // https://ffmpeg.org/doxygen/trunk/structAVPacket.html
  AVPacket *pPacket = av_packet_alloc();
  if (!pPacket) {
    llog(ERROR, "failed to allocate memory for AVPacket");
    return MAKE_P_STAT(FAILED_CREATE_PACKET);
  }

  AVFormatContext* pFormatContext = c->formatCtx;

  int av_rc = 0;
  uint64_t rc = 0;

  for (auto o : c->mediaProcessors) {
    uint64_t frc = 0;
    frc = o->preFirstFrame();
    if (isError(frc))
      goto end;
  }

  if (throttle)
    throttle->start();

  // fill the Packet with data from the Stream
  // https://ffmpeg.org/doxygen/trunk/group__lavf__decoding.html#ga4fdb3084415a82e3810de6ee60e46a61
  while (!c->stopMe)
  {
    // time full read packet processing loop
    {
      TIME_GUARD(read_and_process_frame);

      if ((av_rc = read_frame(pFormatContext, pPacket)) < 0)
        break;

      logPacket(TRACE, COMPONENT, "Packet", pPacket, pFormatContext);

      const int streamIndex = (int)pPacket->stream_index;
      const AVMediaType mediaType = c->streamTypes[streamIndex];
      if (dontSkipPacket(throttle, pFormatContext, pPacket)) {
        for (auto o : c->mediaProcessors) {
          {
            TIME_GUARD(hande_packet);
            const AVStream* stream = pFormatContext->streams[streamIndex];
            rc = o->handlePacket(pPacket, mediaType);
          }

          if (isError(rc))
            break;
        }
      }

      // https://ffmpeg.org/doxygen/trunk/group__lavc__packet.html#ga63d5a489b419bd5d45cfd09091cbcbc2
      av_packet_unref(pPacket);

      if (isError(rc))
        break;
    }
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
