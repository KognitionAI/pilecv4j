/*
 * MediaDataSource.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _MEDIADATASOURCE_H_
#define _MEDIADATASOURCE_H_

#include <stdint.h>
#include <string>
#include <vector>

extern "C" {
#include <libavformat/avformat.h>
}

#include "utils/pilecv4j_ffmpeg_utils.h"

namespace pilecv4j
{
namespace ffmpeg
{

class MediaDataSource
{
public:
  MediaDataSource() = default;
  virtual ~MediaDataSource() = default;

  /**
   * This will be called with preallocated AVFormatContext. The options dictionary will be non-null
   * if there are any options.
   *
   * @note The preallocated AVFormatContext should be freed by the open call if the open call is
   * going to return an error. This is because most open calls will inevitably call avformat_open_input
   * which STUPIDLY, according to the documentation, frees a user supplied AVFormatContext.
   */
  virtual uint64_t open(AVFormatContext* preallocatedAvFormatCtx, AVDictionary** options) = 0;

  virtual std::string toString() = 0;
};

}
} /* namespace pilecv4j */

#endif /* _MEDIADATASOURCE_H_ */
