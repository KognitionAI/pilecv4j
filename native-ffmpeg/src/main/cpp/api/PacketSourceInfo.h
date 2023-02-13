/*
 * PacketSourceInfo.h
 *
 *  Created on: Jan 31, 2023
 *      Author: jim
 */

#ifndef SRC_MAIN_CPP_API_PACKETSOURCEINFO_H_
#define SRC_MAIN_CPP_API_PACKETSOURCEINFO_H_

extern "C" {
#include <libavformat/avformat.h>
}
#include <stdint.h>

struct AVStream;

namespace pilecv4j
{
namespace ffmpeg
{

class MediaContext;

class PacketSourceInfo {
public:
  virtual ~PacketSourceInfo() = default;

  /**
   * This will return the stream for the given index. The index
   * should be less than numStreams(). This can return nullptr
   * if the stream can't be recognized or is somehow tagged
   * to not be processed.
   */
  virtual uint64_t getStream(int streamIndex, AVStream** streamOut) = 0;

  virtual uint64_t numStreams(int* numStreamsOut) = 0;

  /**
   * Retrieve the tag through tagOut and return the status.
   */
  virtual uint64_t getCodecTag(AVCodecID codecId, unsigned int* tagOut) = 0;
};

}

} /* namespace pilecv4j */

#endif /* SRC_MAIN_CPP_API_PACKETSOURCEINFO_H_ */
