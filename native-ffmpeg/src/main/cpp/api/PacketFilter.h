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

namespace pilecv4j
{
namespace ffmpeg
{

class PacketFilter
{
public:
  PacketFilter() = default;
  virtual ~PacketFilter() = default;

  virtual uint64_t setup(AVFormatContext* formatCtx) = 0;

  virtual bool filter(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType) = 0;

};

}
} /* namespace pilecv4j */

#endif /* _PILECV4J_FFMPEG_PACKETFILTER_H_ */
