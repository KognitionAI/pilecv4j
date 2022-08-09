/*
 * StreamContext.h
 *
 *  Created on: Jul 14, 2022
 *      Author: jim
 */

#ifndef _STREAMCONTEXT_H_
#define _STREAMCONTEXT_H_

#include "utils/Synchronizer.h"
#include "utils/log.h"

#include "api/MediaDataSource.h"
#include "api/MediaProcessor.h"

#include <atomic>

namespace pilecv4j
{
namespace ffmpeg
{

// =====================================================
/**
 * The StreamContext structure. This holds all of the state information associated with
 * a stream.
 */
// ==================================
/**
 * The StreamContext goes through a set of states in its lifecycle.
 * These are those states.
 *
 * These need to be kept in sync with the java file FfmpegApi2.java
 */
enum StreamContextState {
  FRESH = 0,
  OPEN = 1,
  LOADED = 2,
  PROCESSORS_SETUP = 3,
  PLAYING = 4,
  STOPPING = 5,
  ENDED = 6
};
// =====================================================

struct StreamDetails;

struct StreamContext {
  /**
   * Current state.
   *
   * The state moves from:
   * FRESH <-> OPEN
   *
   */
  std::atomic<StreamContextState> state;

  MediaDataSource* mediaDataSource = nullptr;

  std::vector<MediaProcessor*> mediaProcessors;

  /**
   * Set of user specified options (e.g. rtsp_transport = tcp).
   */
  std::vector<std::tuple<std::string,std::string> > options;

  /**
   * Stream Container context. It basically represents the input stream's header.
   *
   * Available after state=OPEN
   */
  AVFormatContext* formatCtx = nullptr;

  /**
   * flag that tells the playing loop to exit.
   */
  std::atomic<bool> stopMe;

  Synchronizer* throttle = nullptr;

  AVMediaType* streamTypes = nullptr;

  inline StreamContext() : state(FRESH), stopMe(false) {
    if (isEnabled(TRACE))
      log(TRACE, "STRC", "In StreamContext() for %" PRId64, (uint64_t)this);
  }

  inline ~StreamContext() {
    if (isEnabled(TRACE))
      log(TRACE, "STRC", "In ~StreamContext() for %" PRId64, (uint64_t)this);
    if (formatCtx != nullptr)
      avformat_free_context(formatCtx);
    if (streamTypes != nullptr)
      delete [] streamTypes;
  }

  inline void sync() {
    throttle = new Synchronizer();
  }

  // Is the source set already?
  inline bool isSourceSet() {
    return mediaDataSource != nullptr;
  }

  // First, you can set a uri to a media stream. ...
  inline uint64_t setSource(MediaDataSource* vds) {
    if (isSourceSet())
      return MAKE_P_STAT(ALREADY_SET);
    if (vds == nullptr)
      return MAKE_P_STAT(SOURCE_NULL);
    mediaDataSource = vds;
    return 0;
  }

  inline uint64_t addProcessor(MediaProcessor* vds) {
    if (vds == nullptr)
      return MAKE_P_STAT(NO_PROCESSOR_SET);
    mediaProcessors.push_back(vds);
    return 0;
  }

  inline uint64_t addOption(const char* key, const char* val) {
    options.push_back(std::tuple<std::string, std::string>(key, val));
    return 0;
  }

  // ==============================================
  // StreamContext lifecycle
  // ==============================================

  /**
   * This will move the context from a FRESH state to an OPEN state by
   * opening the input. play() will execute this step if it hasn't been
   * executed yet.
   */
  uint64_t open();

  /**
   * This will move the context from an OPEN state to an LOADED state by
   * loading the input streams and info. play() will execute this step if it hasn't been
   * executed yet.
   */
  uint64_t load();

  /**
   * This will move the context from a LOADED state to an PROCESSORS_SETUP state by
   * calling setup() on all of the processors. play() will execute this step if it hasn't been
   * executed yet.
   */
  uint64_t setupProcessors();

  /**
   * Play will move through all lifecycle stages that haven't been explicitly
   * called yet and then process the input data. It will move the state to
   * PLAYING for as long as there is input data to process. An EOF or
   * calling stop() will cause play() to end moving the stream into an ENDED
   * state.
   */
  uint64_t play();

  /**
   * This can be called to stop a playing stream. It will move the state to STOPPING.
   * Once play() ends the stream will be moved to the ENDED state.
   */
  uint64_t stop();


  uint64_t getStreamDetails(StreamDetails** ppdetails, int* nb);
private:

  uint64_t advanceStateTo(StreamContextState toAdvanceTo);

};

}
} /* namespace pilecv4j */

#endif /* SRC_MAIN_CPP_STREAMCONTEXT_H_ */
