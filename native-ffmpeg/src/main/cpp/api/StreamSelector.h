/*
 * StreamSelector.h
 *
 *  Created on: Jul 12, 2022
 *      Author: jim
 */

#ifndef _STREAMSELECTOR_H_
#define _STREAMSELECTOR_H_

extern "C" {
#include <libavformat/avformat.h>
}

#include <stdint.h>

namespace pilecv4j
{
namespace ffmpeg
{

class StreamSelector
{
public:
  StreamSelector() = default;
  virtual ~StreamSelector() = default;

  /**
   * Given the input AVFormatContext, select which streams should be used by setting the values of the
   * useStreams array to true where the stream should be used and false where the stream shouldn't be
   * used.
   */
  virtual uint64_t selectStreams(AVFormatContext* formatCtx, bool* useStreams, int32_t numStreams) = 0;

};

}
} /* namespace pilecv4j */

#endif /* _STREAMSELECTOR_H_ */
