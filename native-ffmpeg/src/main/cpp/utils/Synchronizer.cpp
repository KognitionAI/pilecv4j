/*
 * Syncronizer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#include "utils/Synchronizer.h"

#include "utils/log.h"

#include <thread>

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "SYNC"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

static bool decide(int64_t timeToDiplayFrame, uint64_t maxDelayMillisBeforeDroppingFrame) {
  bool skipIt = false;

  int64_t curTime = now();
  if (curTime < timeToDiplayFrame) {
    llog(TRACE, "time to present frame (%" PRId64 ") > now (%" PRId64 ")", timeToDiplayFrame, curTime);
    int64_t sleeptime = (timeToDiplayFrame - curTime);
    llog(TRACE, "Sleeping for %d", sleeptime);
    std::this_thread::sleep_for(std::chrono::milliseconds(sleeptime));
  }
  else if ((uint64_t)(curTime - timeToDiplayFrame) > maxDelayMillisBeforeDroppingFrame) {
    llog(TRACE, "time to present frame (%" PRId64 ") < now (%" PRId64 ")", timeToDiplayFrame, curTime);
    llog(DEBUG, "Throwing away frame because it's %d milliseconds late.",(curTime - timeToDiplayFrame) );
    skipIt=true;
  } else
    llog(TRACE, "time to present frame (%" PRId64 ") slightly < now (%" PRId64 ")", timeToDiplayFrame, curTime);

  return skipIt;

}

bool Synchronizer::throttle(int64_t pts, AVRational& time_base) {
  int64_t timeToDiplayFrame = av_rescale_q(pts, time_base, millisecondTimeBase) + startPlayTime;

  return decide(timeToDiplayFrame, maxDelayMillisBeforeDroppingFrame);
}

bool Synchronizer::throttle(const AVFormatContext* fmt, const AVPacket* pPacket) {
  AVRational time_base = fmt->streams[pPacket->stream_index]->time_base;

  int64_t pts;

  // if the input stream has no valid pts (like when reading the live feed from milestone)
  // then we're going to calculate it.
  llog(TRACE, "        ->pPacket,ofc %" PRId64 ", %d / %d", pPacket, time_base.num, time_base.den);
  if (pPacket->pts == AV_NOPTS_VALUE) {
    pts = av_rescale_q((int64_t)(now() - startPlayTime), millisecondTimeBase, time_base);

    llog(TRACE, "calced  ->pts %" PRId64, pts);
  } else
    pts = pPacket->pts;

  int64_t timeToDiplayFrame = av_rescale_q(pts, time_base, millisecondTimeBase) + startPlayTime;

  return decide(timeToDiplayFrame, maxDelayMillisBeforeDroppingFrame);
}

bool Synchronizer::throttle(AVRational& streamTimeBase, AVFrame *pFrame) {
  int64_t timeToDiplayFrame = av_rescale_q(pFrame->best_effort_timestamp, streamTimeBase, millisecondTimeBase) + startPlayTime;

  return decide(timeToDiplayFrame, maxDelayMillisBeforeDroppingFrame);
}

}
} /* namespace pilecv4j */
