/*
 * MediaOutput.h
 *
 *  Created on: Aug 9, 2022
 *      Author: jim
 */

#include "utils/pilecv4j_ffmpeg_utils.h"

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
 * 2. open ~ avformat_alloc_output_context2. Think of this as the place where the output context is created.
 * 3. create streams ~ avformat_new_stream. Think of this as the place where the streams are created
 * 4. ready ~ avformat_write_header. Think of this as the place where the headers are written
 * 5. for each packet; write packet.
 * ...
 * 6. close ~ av_write_trailer + avio_closep + avformat_free_context. Think of this as the place where
 * the trailer is written, the file is closed (if tehre is a file) and the output format context is freed.
 * Once all of the processing is done on the output_format_context this step will be called.
 *
 *
 * if any step fails, fail() will be called.
 */
class Muxer
{
  bool loggedPacketPtsDtsMissingAlready = false;
  int nb_streams = -1;
  int64_t* starting_ts_offset = nullptr;
protected:

  // some helpers

  /**
   * Create a stream in the output_format_context using the AVCodecParameters as a reference
   * and if out is not null, return the newly created stream
   */
  static uint64_t createStreamFromCodecParams(AVFormatContext* outputCtx, AVCodecParameters* pars, AVStream** out);

  /**
   * Create a stream in the output_format_context using the AVCodecContext (the AVCodec must be set on the AVCodecContext)
   * and if out is not null, return the newly created stream
   */
  static uint64_t createStreamFromCodec(AVFormatContext* outputCtx, AVCodecContext* pars, AVStream** out);

public:
  inline Muxer() = default;

  inline virtual ~Muxer() {
    if (starting_ts_offset)
      delete [] starting_ts_offset;
  }

  /**
   * Implementers are responsible for cleanup here including rewriting header, closing output,
   * and freeing the output format context.
   *
   * NOTE: close should be idempotent
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
   * It is fine for 'stream_index_out' to be null.
   */
  virtual uint64_t createNextStream(AVCodecParameters* codecPars, int* stream_index_out) = 0;

  /**
   * This will be called if we have the AVCodec. If we only have the AVCodecParameters
   * then createNextStream(AVCodecParameters*) will be called instead.
   *
   * This will also set the stream's time_base to the codec_params time base which acts
   * as a suggestion to the muxer. The muxer is free to change this on avformat_write_header.
   *
   * Also it will set the stream->codecpar to the codec context's codecpar.
   *
   * It is fine for 'stream_index_out' to be null.
   */
  virtual uint64_t createNextStream(AVCodecContext* codec, int* stream_index_out) = 0;

  /**
   * This is essentially where the avformat_write_header should be called on the output
   */
  virtual uint64_t ready() = 0;

  /**
   * On failure there can be some resources that need closing. This will be called to notify the muxer
   * of a failure.
   *
   * NOTE: fail() should be idempotent
   */
  virtual void fail() = 0;

  /**
   * Have the Muxer try to figure out the output format based. It's possible for this to return null
   * if it can't figure  it out.
   */
  virtual const AVOutputFormat* guessOutputFormat() = 0;

  /**
   * This is a simple interleaved write to the output context.
   *
   * The packet is assumed to have been already translated to the output stream meaning
   * the pts and dts already corresponds to the output stream's time_base and the stream
   * index is the output's stream index.
   */
  inline virtual uint64_t writeFinalPacket(AVPacket* outputPacket) {
    int rc = av_interleaved_write_frame(getFormatContext(), outputPacket);
    if (rc != 0) {
      log(ERROR, "MUXR", "Error %d while writing packet to output: %s", rc, av_err2str(rc));
      return MAKE_AV_STAT(rc);
    }
    return 0;
  }

  /**
   * This is a simple interleaved write to the output context.
   * The packet's timing is assumed to be in terms of the inputPacketTimeBase and will be translated
   * to the output time_base before writing (the inputPacket will not be touched, hence the const) and
   * will be written to the provided output_stream_index
   */
  virtual uint64_t writePacket(const AVPacket* inputPacket, const AVRational& inputPacketTimeBase, int output_stream_index);

  /**
   * If you need direct access to the stream you can get it, if it exists.
   */
  inline AVStream* getStream(int index) {
    if (index < 0)
      return nullptr;

    AVFormatContext* ctx = getFormatContext();
    if (ctx) {
      const int nb_streams = ctx->nb_streams;
      if (index >= nb_streams)
        return nullptr;
      return ctx->streams[index];
    }
    return nullptr;
  }

  /**
   * When muxing you can call this prior to ready() (avformat_write_header). If it's called before the stream is
   * created it will return NO_STREAM. If it's called after read is called then the results are unknown but
   * will likely be ignored.
   */
  inline uint64_t setStreamTimebase(int stream_index, const AVRational& time_base) {
    AVStream* stream = getStream(stream_index);
    if (!stream)
      return MAKE_P_STAT(NO_STREAM);
    stream->time_base = time_base;
    return 0;
  }
};

}
} /* namespace pilecv4j */

#endif /* _PILECV4J_FFMPEG_MEDIAOUTPUT_H_ */
