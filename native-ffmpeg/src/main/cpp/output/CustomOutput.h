/*
 * UriRemuxer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_
#define _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_

#include "api/MediaOutput.h"

#include <string>

#define DEFAULT_MAX_REMUX_ERRORS 20

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * For custom IO, this will be responsible for reading bytes from the stream
 */
typedef uint64_t (*write_buffer)(int32_t numBytes);

#ifdef __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP
class CustomOutput;
static int write_packet_to_custom_source(void *opaque, uint8_t *buf, int buf_size);
static inline void* fetchBuffer(CustomOutput*);
#endif

//#define PCV4J_CUSTOMIO_OUTPUT_BUFSIZE 8192
#define PCV4J_CUSTOMIO_OUTPUT_BUFSIZE 1048510

/**
 * This is a media processor that will remux all video and audio packets that make
 * it to the handlePacket call.
 */
class CustomOutput: public MediaOutput
{
  std::string fmt;
  bool fmtNull;
  std::string outputUri;
  bool outputUriNull;
  bool cleanupIoContext = false; // track whether or not we need to cleanup the io context

  AVIOContext* ioContext = nullptr;
  uint8_t* ioBuffer = nullptr;
  uint8_t* ioBufferToWriteToJava = nullptr;
  write_buffer dataSupplyCallback = nullptr;

#ifdef __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP
  friend int write_packet_to_custom_source(void *opaque, uint8_t *buf, int buf_size);
  friend void* fetchBuffer(CustomOutput*);
#endif

  void cleanup(bool writeTrailer);

public:
  inline CustomOutput(const char* pfmt, const char* poutputUri) :
     fmt(pfmt == nullptr ? "" : pfmt), fmtNull(pfmt == nullptr),
    outputUri(poutputUri == nullptr ? "" : poutputUri), outputUriNull(poutputUri == nullptr) {
    ioBufferToWriteToJava = (uint8_t*)malloc(PCV4J_CUSTOMIO_OUTPUT_BUFSIZE * sizeof(uint8_t));
  }

  inline void set(write_buffer callback) {
    dataSupplyCallback = callback;
  }

  virtual ~CustomOutput();

  virtual uint64_t allocateOutputContext(AVFormatContext **);

  virtual uint64_t openOutput(AVDictionary** opts);

  virtual uint64_t close();

  virtual void fail();
};

#ifdef __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP
static inline void* fetchBuffer(CustomOutput* c) {
  return c->ioBufferToWriteToJava;
}
#endif

}
} /* namespace pilecv4j */

#endif /* _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_ */
