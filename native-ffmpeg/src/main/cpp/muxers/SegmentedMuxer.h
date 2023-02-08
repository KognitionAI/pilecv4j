/*
 * UriRemuxer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_
#define _pilecv4j_ffmpeg_CUSTOMOUTPUTREMUXER_H_

#include "../api/Muxer.h"
#include <functional>
#include <vector>

#define DEFAULT_MAX_REMUX_ERRORS 20

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * Supply a muxer given the muxer count. This MUST set the muxer pointer to nullptr
 * if it returns an error.
 */
typedef std::function <uint64_t(uint64_t muxCount, Muxer** outMuxer)> MuxerSupplier;
typedef std::function <bool(const AVPacket* packet, const AVMediaType mediaType, const AVRational& tb)> CloseSegmentP;

class StreamCreator;

/**
 * This is a media processor that will remux all video and audio packets that make
 * it to the handlePacket call.
 */
class SegmentedMuxer: public Muxer
{
  bool closed = false;
  MuxerSupplier muxerSupplier;
  CloseSegmentP closeSeg;

  Muxer* currentMuxer = nullptr;
  uint64_t muxCount = 0;
  std::vector<StreamCreator*> streamCreators;
  std::vector<AVMediaType> streamMediaTypes;
  bool pendingClose = false;

  uint64_t rotate();
  int reference_stream = -1;

public:
  inline SegmentedMuxer(const MuxerSupplier pMutexSupplier, CloseSegmentP pcloseSeg) :
    muxerSupplier(pMutexSupplier), closeSeg(pcloseSeg) {  }

  virtual ~SegmentedMuxer();

  virtual uint64_t open();

  virtual AVFormatContext* getFormatContext();

  /**
   * This will be called if we have the AVCodecParameters. If we have the actual AVCodec
   * then createNextStream(AVCodec*) will be called instead.
   */
  virtual uint64_t createNextStream(AVCodecParameters* codecPars, int* stream_index_out);

  /**
   * This will be called if we have the AVCodec. If we only have the AVCodecParameters
   * then createNextStream(AVCodecParameters*) will be called instead.
   */
  virtual uint64_t createNextStream(AVCodecContext* codec, int* stream_index_out);

  /**
   * This is essentially where the avformat_write_header should be called on the output
   */
  virtual uint64_t ready();

  /**
   * This is a simple interleaved write to the output context.
   * The packet's timing is assumed to be in terms of the inputPacketTimeBase and will be translated
   * to the output time_base before writing (the inputPacket will not be touched, hence the const) and
   * will be written to the provided output_stream_index
   */
  virtual uint64_t writePacket(const AVPacket* inputPacket, const AVRational& inputPacketTimeBase, int output_stream_index);

//  /**
//   * The packet is assumed to have been already translated to the output stream meaning
//   * the pts and dts already corresponds to the output stream's time_base and the stream
//   * index is the output's stream index.
//   */
//  virtual uint64_t writePacket(AVPacket* outputPacket);

  /**
   * By default, if the output_format_context is not null, this will write the trailer
   * using <em>av_write_trailer</em>
   */
  virtual uint64_t close();

  /**
   * This will be called if a failure occurs in a class using the muxer as a notification to
   * the muxer to clean up resources because of a failure.
   */
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
