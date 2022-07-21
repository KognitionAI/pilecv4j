/*
 * UriVideoDataSource.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _URIMEDIADATASOURCE_H_
#define _URIMEDIADATASOURCE_H_

#include "api/MediaDataSource.h"

namespace pilecv4j
{
namespace ffmpeg
{

class UriMediaDataSource: public MediaDataSource
{
  std::string uri;
public:
  inline UriMediaDataSource(const char* puri): uri(puri)
  {  }

  virtual ~UriMediaDataSource() = default;

  virtual uint64_t open(AVFormatContext* preallocatedAvFormatCtx, AVDictionary** options);

  inline std::string toString() {
    return uri;
  }

};

}
} /* namespace pilecv4j */

#endif /* _URIMEDIADATASOURCE_H_ */
