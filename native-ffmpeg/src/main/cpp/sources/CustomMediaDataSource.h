/*
 * CustomMediaDataSource.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _CUSTOMMEDIADATASOURCE_H_
#define _CUSTOMMEDIADATASOURCE_H_

#include "api/MediaDataSource.h"

#include "common/pilecv4j_utils.h"

#define PCV4J_CUSTOMIO_BUFSIZE 8192

using namespace ai::kognition::pilecv4j;

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * For custom IO, this will be responsible for reading bytes from the stream
 */
typedef int32_t (*fill_buffer)(int32_t numBytesMax);

/**
 * For custom IO, this will be responsible for seeking within a stream.
 */
typedef int64_t (*seek_buffer)(int64_t offset, int whence);

#ifdef __INSIDE_CUSTOM_MEDIA_DATA_SOURCE_CPP
static int read_packet_from_custom_source(void *opaque, uint8_t *buf, int buf_size);
static int64_t seek_in_custom_source(void *opaque, int64_t offset, int whence);
#endif

class CustomMediaDataSource: public MediaDataSource
{
  fill_buffer dataSupplyCallback = nullptr;
  seek_buffer seekCallback = nullptr;

  /**
   * When supplying your own data for decode, these will be set after state=OPEN.
   */
  AVIOContext* ioContext = nullptr;
  uint8_t* ioBuffer = nullptr;

#ifdef __INSIDE_CUSTOM_MEDIA_DATA_SOURCE_CPP
  friend int read_packet_from_custom_source(void *opaque, uint8_t *buf, int buf_size);
  friend int64_t seek_in_custom_source(void *opaque, int64_t offset, int whence);
#endif

public:
  uint8_t* ioBufferToFillFromJava = nullptr;

  inline CustomMediaDataSource() {
    ioBufferToFillFromJava = (uint8_t*)malloc(PCV4J_CUSTOMIO_BUFSIZE * sizeof(uint8_t));
  }

  virtual ~CustomMediaDataSource();

  inline void set(fill_buffer callback, seek_buffer pseekCallback) {
    dataSupplyCallback = callback;
    seekCallback = pseekCallback;
  }

  virtual uint64_t open(AVFormatContext* preallocatedAvFormatCtx, AVDictionary** options);

  virtual inline std::string toString() {
    return StringFormat("CustomFormat(%" PRId64 ", %" PRId64 ")", (uint64_t)dataSupplyCallback, (uint64_t)seekCallback);
  }

  inline bool seekable() {
    return seekCallback != nullptr;
  }
  };

}
} /* namespace pilecv4j */

#endif /* _CUSTOMMEDIADATASOURCE_H_ */
