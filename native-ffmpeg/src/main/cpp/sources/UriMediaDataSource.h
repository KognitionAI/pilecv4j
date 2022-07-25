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
  std::string fmt;
  bool fmtNull;
  std::string uri;
  bool uriNull;
public:
  inline UriMediaDataSource(const char* puri): fmtNull(true), uri(puri), uriNull(!puri)
  {  }

  inline UriMediaDataSource(const char* pfmt, const char* puri): fmt(pfmt), fmtNull(!pfmt), uri(puri), uriNull(!puri)
  {  }

  virtual ~UriMediaDataSource() = default;

  virtual uint64_t open(AVFormatContext* preallocatedAvFormatCtx, AVDictionary** options);

  inline std::string toString() {
    return uriNull ? "null" : uri;
  }

};

}
} /* namespace pilecv4j */

#endif /* _URIMEDIADATASOURCE_H_ */
