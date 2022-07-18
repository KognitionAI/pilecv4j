/*
 * Syncronizer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _SYNCRONIZER_H_
#define _SYNCRONIZER_H_

extern "C" {
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
}

#include <stdint.h>
#include "utils/pilecv4j_ffmpeg_utils.h"

#define DEFAULT_SYNCRONIZER_MAX_DELAY_MILLIS 1000

namespace pilecv4j
{

/**
 * This class can be used to play back a stream at a natural rate. It needs
 * to be initialized with the stream's timebase which can be supplied directly
 * or it can be gleaned from the AVStream.
 */
class Synchronizer
{
  int64_t startPlayTime;

  /**
   * When sync = true, what's the max delay before we need to start discarding frames
   * by skipping calls to the frame callback in order to try to catch up.
   */
  uint64_t maxDelayMillisBeforeDroppingFrame;

public:
  /**
   * Set the start time JUST IN CASE someone forgets to call start.
   */
  inline Synchronizer(uint64_t pmaxDelayMillisBeforeDroppingFrame = DEFAULT_SYNCRONIZER_MAX_DELAY_MILLIS)
    : startPlayTime(now()), maxDelayMillisBeforeDroppingFrame(pmaxDelayMillisBeforeDroppingFrame) {}

  ~Synchronizer() = default;

  /**
   * This should be called on the first frame to begin the timing.
   */
  inline void start() {
    startPlayTime = now();
  }

  /**
   * If necessary, delay until the display time for the frame has arrived. If we're behind,
   * return 'true' to indicate the packet should be skipped.
   */
  bool throttle(AVRational& streamTimeBase, AVFrame *pFrame);

  bool throttle(AVFormatContext* fmt, AVPacket* pPacket);

private:

};

} /* namespace pilecv4j */

#endif /* _SYNCRONIZER_H_ */
