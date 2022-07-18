/*
 * VideoProcessor.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _MEDIAPROCESSOR_H_
#define _MEDIAPROCESSOR_H_

#include <vector>

extern "C" {
#include <libavformat/avformat.h>
}

#include "utils/pilecv4j_ffmpeg_utils.h"

namespace pilecv4j
{

class MediaProcessor
{
public:
  MediaProcessor() = default;
  virtual ~MediaProcessor() = default;

  /**
   * Override this to prepare for handling the stream. The AVFormatContext will already
   * have had the avformat_find_stream_info called so there's no need to do it
   * inside prepStream.
   *
   * NOTE: selected streams can be null.
   */
  virtual uint64_t setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams) = 0;

  /**
   * This will be called just before calling handlePacket for the first time. If timers
   * are used they can be initialized here.
   */
  virtual uint64_t preFirstFrame(AVFormatContext* avformatCtx) = 0;

  /**
   * Handle each packed being provided by the data source.
   */
  virtual uint64_t handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType packetMediaType) = 0;

protected:

  // Helper methods for implementing sub classes.

  /**
   * Open a codec and create a context for the given stream and return the status. *codecCtxPtr is
   * set to nullptr if an error is returned. Otherwise it should contain a valid AVCodecContext
   * for the given stream.
   */
  static uint64_t open_codec(AVStream* pStream, AVDictionary** options,  AVCodecContext** codecCtxPtr);

  /**
   * Open a codec and create a context for the given stream referenced in the AVFormatContext by the
   * given index, and return the status. *codecCtxPtr is set to nullptr if an error is returned.
   * Otherwise it should contain a valid AVCodecContext for the given stream.
   */
  static inline uint64_t open_codec(AVFormatContext* formatCtx, int streamIndex, AVDictionary** options, AVCodecContext** codecCtxPtr) {
    AVStream* pStream = formatCtx->streams[streamIndex];
    return open_codec(pStream, options, codecCtxPtr);
  }

};

} /* namespace pilecv4j */

#endif /* _MEDIAPROCESSOR_H_ */
