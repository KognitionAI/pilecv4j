/*
 * JavaStreamSelector.h
 *
 *  Created on: Jul 17, 2022
 *      Author: jim
 */

#ifndef _JAVASTREAMSELECTOR_H_
#define _JAVASTREAMSELECTOR_H_

#include "api/StreamSelector.h"

namespace pilecv4j
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

class JavaStreamSelector: public StreamSelector
{
  select_streams callback;

public:
  inline JavaStreamSelector(select_streams cb) : callback(cb) {}
  virtual ~JavaStreamSelector() = default;

  virtual uint64_t selectStreams(AVFormatContext* formatCtx, bool* useStreams, int32_t numStreams);
};

} /* namespace pilecv4j */

#endif /* _JAVASTREAMSELECTOR_H_ */
