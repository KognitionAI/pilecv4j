/*
 * MediaOutput.h
 *
 *  Created on: Aug 9, 2022
 *      Author: jim
 */

#include "api/MediaProcessor.h"

extern "C" {
#include <libavformat/avformat.h>
}

#ifndef _PILECV4J_FFMPEG_MEDIAOUTPUT_H_
#define _PILECV4J_FFMPEG_MEDIAOUTPUT_H_

namespace pilecv4j
{
namespace ffmpeg
{

class Muxer : public MediaProcessor
{
  bool closed = false;
protected:

public:
  inline Muxer() = default;

  virtual ~Muxer();

  inline bool isClosed() {
    return closed;
  }

  virtual inline uint64_t close() {
    closed = true;
    return 0;
  }

};

}
} /* namespace pilecv4j */

#endif /* _PILECV4J_FFMPEG_MEDIAOUTPUT_H_ */
