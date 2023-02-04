/*
 * UriRemuxer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_
#define _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_

#include "../api/Muxer.h"
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
class DefaultMuxer;
static int write_packet_to_custom_source(void *opaque, uint8_t *buf, int buf_size);
static inline void* fetchBuffer(DefaultMuxer*);
#endif

//#define PCV4J_CUSTOMIO_OUTPUT_BUFSIZE 8192
#define PCV4J_CUSTOMIO_OUTPUT_BUFSIZE 1048510

/**
 * This is a media processor that will remux all video and audio packets that make
 * it to the handlePacket call.
 */
class DefaultMuxer: public Muxer
{
  std::string fmt;
  bool fmtNull;
  std::string outputUri;
  bool outputUriNull;
  bool cleanupIoContext = false; // track whether or not we need to cleanup the io context

  AVIOContext* ioContext = nullptr;
  uint8_t* ioBuffer = nullptr;
  uint8_t* ioBufferToWriteToJava;
  write_buffer dataSupplyCallback = nullptr;

  bool closed = false;
  AVFormatContext* output_format_context = nullptr;

#ifdef __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP
  friend int write_packet_to_custom_source(void *opaque, uint8_t *buf, int buf_size);
  friend void* fetchBuffer(DefaultMuxer*);
#endif

  void cleanup(bool writeTrailer);
  uint64_t allocateOutputContext(AVFormatContext **);

public:
  inline DefaultMuxer(const char* pfmt, const char* poutputUri, write_buffer callback) :
     fmt(pfmt == nullptr ? "" : pfmt), fmtNull(pfmt == nullptr),
     outputUri(poutputUri == nullptr ? "" : poutputUri), outputUriNull(poutputUri == nullptr),
     dataSupplyCallback(callback) {
    ioBufferToWriteToJava = callback ? (uint8_t*)malloc(PCV4J_CUSTOMIO_OUTPUT_BUFSIZE * sizeof(uint8_t)) : nullptr;
  }

  virtual ~DefaultMuxer();

  virtual uint64_t open(AVDictionary** opts);

  inline virtual AVFormatContext* getFormatContext() {
    return output_format_context;
  }

  /**
   * This will be called if we have the AVCodecParameters. If we have the actual AVCodec
   * then createNextStream(AVCodec*) will be called instead.
   */
  virtual uint64_t createNextStream(AVCodecParameters* codecPars, AVStream** out);

  /**
   * This will be called if we have the AVCodec. If we only have the AVCodecParameters
   * then createNextStream(AVCodecParameters*) will be called instead.
   */
  virtual uint64_t createNextStream(AVCodec* codec, AVStream** out);

  uint64_t ready();
  /**
   * By default, if the output_format_context is not null, this will write the trailer
   * using <em>av_write_trailer</em>
   */
  virtual uint64_t close();

  virtual void fail();
};

#ifdef __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP
static inline void* fetchBuffer(DefaultMuxer* c) {
  return c->ioBufferToWriteToJava;
}
#endif

}
} /* namespace pilecv4j */

#endif /* _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_ */
