/*
 * JavaStreamSelector.h
 *
 *  Created on: Jul 17, 2022
 *      Author: jim
 */

#ifndef _JAVASTREAMSELECTOR_H_
#define _JAVASTREAMSELECTOR_H_

#include "api/PacketFilter.h"

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * The callback will be passed the number of streams and also a preallocated array
 * of int32_ts it needs to treat as booleans. The array will be defaulted to 'true'.
 * The callback needs to decide which streams should be processed by setting each
 * entry to true that corresponds to streams to be processed and false to streams
 * that should be blocked.
 *
 * It should return 1 if successful, and 0 to flag a failure.
 */
typedef int32_t (*select_streams)(int32_t numStreams, int32_t* selected);

class JavaStreamSelector: public PacketFilter
{
  select_streams callback;

  bool* useStreams = nullptr;
  int numStreams = 0;

public:
  inline JavaStreamSelector(select_streams cb) : callback(cb) {}
  virtual ~JavaStreamSelector() = default;

  virtual uint64_t setup(PacketSourceInfo* mediaSource, const std::vector<std::tuple<std::string,std::string> >& options);

  virtual bool filter(AVPacket* pPacket, AVMediaType streamMediaType);
};

}
} /* namespace pilecv4j */

#endif /* _JAVASTREAMSELECTOR_H_ */
