/*
 * MediaProcessorChain.h
 *
 *  Created on: Jul 13, 2022
 *      Author: jim
 */

#ifndef _MEDIAPROCESSORCHAIN_H_
#define _MEDIAPROCESSORCHAIN_H_

#include <vector>

#include "api/MediaProcessor.h"
#include "api/PacketFilter.h"
#include "utils/pilecv4j_ffmpeg_utils.h"

namespace pilecv4j
{
namespace ffmpeg
{

class MediaProcessorChain : public MediaProcessor
{
  std::vector<PacketFilter*> packetFilters;
  std::vector<MediaProcessor*> mediaProcessors;

  PacketSourceInfo* packetSource = nullptr;
  std::vector<std::tuple<std::string,std::string> > options;
public:
  MediaProcessorChain() = default;
  virtual ~MediaProcessorChain();

  virtual uint64_t setup(PacketSourceInfo* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options);
  virtual uint64_t preFirstFrame();
  virtual uint64_t handlePacket(AVPacket* pPacket, AVMediaType streamMediaType);

  virtual inline uint64_t close() {
    return 0;
  }

  inline uint64_t addProcessor(MediaProcessor* vds) {
    if (vds == nullptr)
      return MAKE_P_STAT(NO_PROCESSOR_SET);
    if (packetSource)
      vds->setup(packetSource, options);
    mediaProcessors.push_back(vds);
    return 0;
  }

  inline uint64_t addPacketFilter(PacketFilter* vds) {
    if (vds == nullptr)
      return MAKE_P_STAT(NO_PROCESSOR_SET);
    if (packetSource)
      vds->setup(packetSource, options);
    packetFilters.push_back(vds);
    return 0;
  }

};

}
} /* namespace pilecv4j */

#endif /* _MEDIAPROCESSORCHAIN_H_ */
