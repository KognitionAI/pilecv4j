/*
 * MediaOutput.h
 *
 *  Created on: Aug 9, 2022
 *      Author: jim
 */

extern "C" {
#include <libavformat/avformat.h>
}

#ifndef _PILECV4J_FFMPEG_MEDIAOUTPUT_H_
#define _PILECV4J_FFMPEG_MEDIAOUTPUT_H_

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * The lifecycle of a Muxer is:
 *
 * 1. construct
 * 2. open
 * 3. create streams
 * 4. ready
 * ...
 * 5. once all of the processing is done on the output_format_context then close/delete will be called.
 *
 * if any step fails, fail() will be called.
 */
class Muxer
{
protected:

  // some helpers

  /**
   * Create a stream in the output_format_context using the AVCodecParameters as a reference
   * and if out is not null, return the newly created stream
   */
  static uint64_t createStreamFromCodecParams(AVFormatContext* outputCtx, AVCodecParameters* pars, AVStream** out);

  /**
   * Create a stream in the output_format_context using the AVCodec
   * and if out is not null, return the newly created stream
   */
  static uint64_t createStreamFromCodec(AVFormatContext* outputCtx, AVCodec* pars, AVStream** out);

public:
  inline Muxer() = default;

  virtual ~Muxer() = default;

  /**
   * Implementers are responsible for cleanup here including rewriting header, closing output,
   * and freeing the output format context
   */
  virtual uint64_t close() = 0;

  /**
   * Implementers need to open the output here including creating the output context
   * and calling setFormatContext if they're not overloading getFormatContext.
   */
  virtual uint64_t open() = 0;

  /**
   * The current output format context should be returned here.
   * This will NOT be called before open
   */
  virtual AVFormatContext* getFormatContext() = 0;

  /**
   * This will be called if we have the AVCodecParameters. If we have the actual AVCodec
   * then createNextStream(AVCodec*) will be called instead.
   *
   * It is fine for 'out' to be null.
   */
  virtual uint64_t createNextStream(AVCodecParameters* codecPars, AVStream** out) = 0;

  /**
   * This will be called if we have the AVCodec. If we only have the AVCodecParameters
   * then createNextStream(AVCodecParameters*) will be called instead.
   *
   * It is fine for 'out' to be null.
   */
  virtual uint64_t createNextStream(AVCodec* codec, AVStream** out) = 0;

  /**
   * This will be called if we have the AVCodec. If we only have the AVCodecParameters
   * then createNextStream(AVCodecParameters*) will be called instead.
   *
   * It is fine for 'out' to be null.
   */
  virtual uint64_t ready() = 0;

  /**
   * On failure there can be some resources that need closing. This will be called to close them.
   * NOTE: fail() should be idempotent
   */
  virtual void fail() = 0;
};

}
} /* namespace pilecv4j */

#endif /* _PILECV4J_FFMPEG_MEDIAOUTPUT_H_ */
