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
typedef int64_t (*seek_buffer_out)(int64_t offset, int whence);

#ifdef __INSIDE_DEFAULT_MUXER_SOURCE_CPP
class DefaultMuxer;
static int write_packet_to_custom_output(void *opaque, uint8_t *buf, int buf_size);
static int64_t seek_in_custom_output(void *opaque, int64_t offset, int whence);
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
  seek_buffer_out seekCallback = nullptr;

  bool closed = false;
  AVFormatContext* output_format_context = nullptr;
  bool createdStreams = false;
  bool readyCalled = false;

#ifdef __INSIDE_DEFAULT_MUXER_SOURCE_CPP
  friend int write_packet_to_custom_output(void *opaque, uint8_t *buf, int buf_size);
  friend int64_t seek_in_custom_output(void *opaque, int64_t offset, int whence);
  friend void* fetchBuffer(DefaultMuxer*);
#endif

  void cleanup(bool writeTrailer);
  uint64_t allocateOutputContext(AVFormatContext **);

  inline bool seekable() {
    return seekCallback != nullptr;
  }

public:
  inline DefaultMuxer(const char* pfmt, const char* poutputUri, write_buffer callback, seek_buffer_out seek) :
     fmt(pfmt == nullptr ? "" : pfmt), fmtNull(pfmt == nullptr),
     outputUri(poutputUri == nullptr ? "" : poutputUri), outputUriNull(poutputUri == nullptr),
     dataSupplyCallback(callback), seekCallback(seek) {
    ioBufferToWriteToJava = callback ? (uint8_t*)malloc(PCV4J_CUSTOMIO_OUTPUT_BUFSIZE * sizeof(uint8_t)) : nullptr;
  }

  virtual ~DefaultMuxer();

  virtual uint64_t open() override;

  inline virtual AVFormatContext* getFormatContext() override {
    return output_format_context;
  }

  /**
   * This will be called if we have the AVCodecParameters. If we have the actual AVCodec
   * then createNextStream(AVCodec*) will be called instead.
   */
  virtual uint64_t createNextStream(AVCodecParameters* codecPars, int* stream_index_out) override;

  /**
   * This will be called if we have the AVCodec. If we only have the AVCodecParameters
   * then createNextStream(AVCodecParameters*) will be called instead.
   */
  virtual uint64_t createNextStream(AVCodecContext* codec, int* stream_index_out) override;

  /**
   * This is essentially where the avformat_write_header should be called on the output
   */
  virtual uint64_t ready() override;

  /**
   * By default, if the output_format_context is not null, this will write the trailer
   * using <em>av_write_trailer</em>
   */
  virtual uint64_t close() override;

  /**
   * This will be called if a failure occurs in a class using the muxer as a notification to
   * the muxer to clean up resources because of a failure.
   */
  virtual void fail() override;

  /**
   * If the internal AVFormatContext is created and already has an oformat set,
   * that will be returned since there will be no "guessing" necessary. Otherwise
   * it will guess based on the format and uri that were specified.
   */
  virtual const AVOutputFormat* guessOutputFormat() override;

};

#ifdef __INSIDE_DEFAULT_MUXER_SOURCE_CPP
static inline void* fetchBuffer(DefaultMuxer* c) {
  return c->ioBufferToWriteToJava;
}
#endif

}
} /* namespace pilecv4j */

#endif /* _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_ */
