/*
 * FirstVideoStreamSelector.h
 *
 *  Created on: Jul 13, 2022
 *      Author: jim
 */

#ifndef _FIRSTVIDEOSTREAMSELECTOR_H_
#define _FIRSTVIDEOSTREAMSELECTOR_H_

#include "api/PacketFilter.h"

namespace pilecv4j
{
namespace ffmpeg
{

class FirstVideoStreamSelector: public PacketFilter
{
  bool* useStreams = nullptr;
  int numStreams = 0;
public:
  FirstVideoStreamSelector() = default;
  virtual ~FirstVideoStreamSelector();

  virtual uint64_t setup(PacketSourceInfo* mediaSource, const std::vector<std::tuple<std::string,std::string> >& options);

  virtual bool filter(AVPacket* pPacket, AVMediaType streamMediaType);
};

}
} /* namespace pilecv4j */

#endif /* _FIRSTVIDEOSTREAMSELECTOR_H_ */
