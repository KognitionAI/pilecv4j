/*
 * FirstVideoStreamSelector.h
 *
 *  Created on: Jul 13, 2022
 *      Author: jim
 */

#ifndef _FIRSTVIDEOSTREAMSELECTOR_H_
#define _FIRSTVIDEOSTREAMSELECTOR_H_

#include "api/StreamSelector.h"

namespace pilecv4j
{
namespace ffmpeg
{

class FirstVideoStreamSelector: public StreamSelector
{
public:
  FirstVideoStreamSelector() = default;
  virtual ~FirstVideoStreamSelector() = default;

  /**
   * This will select only the first decodable video stream
   */
  virtual uint64_t selectStreams(AVFormatContext* formatCtx, bool* useStreams, int32_t numStreams);

};

}
} /* namespace pilecv4j */

#endif /* _FIRSTVIDEOSTREAMSELECTOR_H_ */
