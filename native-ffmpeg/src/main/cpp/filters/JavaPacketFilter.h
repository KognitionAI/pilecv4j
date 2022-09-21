/*
 * JavaStreamSelector.h
 *
 *  Created on: Jul 17, 2022
 *      Author: jim
 */

#ifndef _PILECF4J_FFMPEG_JAVAPACKETFILTER_H_
#define _PILECF4J_FFMPEG_JAVAPACKETFILTER_H_

#include "api/PacketFilter.h"

namespace pilecv4j
{
namespace ffmpeg
{

typedef int32_t (*packet_filter)(int32_t mediaType, int32_t stream_index, int32_t packetNumBytes,
    int32_t isKeyFrame, int64_t pts, int64_t dts, int32_t tbNum, int32_t tbDen);

class JavaPacketFilter: public PacketFilter
{
  packet_filter callback;

public:
  inline JavaPacketFilter(packet_filter cb) : callback(cb) {}
  virtual ~JavaPacketFilter() = default;

  virtual bool filter(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType);

  virtual inline uint64_t setup(AVFormatContext* formatCtx) {
    return 0L;
  }
};

}
} /* namespace pilecv4j */

#endif /* _PILECF4J_FFMPEG_JAVAPACKETFILTER_H_ */
