/*
 * VideoProcessor.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _MEDIAPROCESSOR_H_
#define _MEDIAPROCESSOR_H_

#include "api/PacketSourceInfo.h"

#include <libavcodec/avcodec.h>
#include <vector>

extern "C" {
#include <libavformat/avformat.h>
}

#include "utils/pilecv4j_ffmpeg_utils.h"

namespace pilecv4j
{
namespace ffmpeg
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
  virtual uint64_t setup(PacketSourceInfo* mediaSource, std::vector<std::tuple<std::string,std::string> >& options) = 0;

  /**
   * This will be called just before calling handlePacket for the first time. If timers
   * are used they can be initialized here.
   */
  virtual inline uint64_t preFirstFrame() {
    return 0;
  }

  /**
   * Handle each packed being provided by the data source.
   */
  virtual uint64_t handlePacket(AVPacket* pPacket, AVMediaType packetMediaType) = 0;

  /**
   * Free resources prior to delete.
   */
  virtual uint64_t close() = 0;

protected:

  // Helper methods for implementing sub classes.

  /**
   * Open a codec and create a context for the given stream and return the status. *codecCtxPtr is
   * set to nullptr if an error is returned. Otherwise it should contain a valid AVCodecContext
   * for the given stream.
   *
   * You can pass a decoderName and it will use that decoder. Otherwise the decoder is inferred from the stream
   */
  static uint64_t open_codec(struct AVStream* pStream, struct AVDictionary** options, struct AVCodecContext** codecCtxPtr, const char* decoderName);

};

}
} /* namespace pilecv4j */

#endif /* _MEDIAPROCESSOR_H_ */
