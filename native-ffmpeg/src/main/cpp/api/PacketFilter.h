/*
 * PacketFilter.h
 *
 *  Created on: Aug 16, 2022
 *      Author: jim
 */

#ifndef _PILECV4J_FFMPEG_PACKETFILTER_H_
#define _PILECV4J_FFMPEG_PACKETFILTER_H_

extern "C" {
#include <libavformat/avformat.h>
}

#include <vector>
#include <string>

namespace pilecv4j
{
namespace ffmpeg
{

class PacketSourceInfo;

class PacketFilter
{
public:
  PacketFilter() = default;
  virtual ~PacketFilter() = default;

  virtual uint64_t setup(PacketSourceInfo* mediaSource, const std::vector<std::tuple<std::string,std::string> >& options) = 0;

  virtual bool filter(AVPacket* pPacket, AVMediaType streamMediaType) = 0;

protected:
  static uint64_t calculateTimeBaseReference(PacketSourceInfo* psi, AVRational** time_bases, int* numStreams);
};

}
} /* namespace pilecv4j */

#endif /* _PILECV4J_FFMPEG_PACKETFILTER_H_ */
