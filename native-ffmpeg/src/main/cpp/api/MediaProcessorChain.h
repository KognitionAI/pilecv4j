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
#include "api/StreamSelector.h"
#include "utils/pilecv4j_ffmpeg_utils.h"

namespace pilecv4j
{
namespace ffmpeg
{

class MediaProcessorChain : public MediaProcessor
{
  StreamSelector* selector = nullptr;
  std::vector<MediaProcessor*> mediaProcessors;

  bool* selectedStreams = nullptr;
  int streamCount = 0;

public:
  MediaProcessorChain() = default;
  virtual ~MediaProcessorChain();

  virtual uint64_t setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams);
  virtual uint64_t preFirstFrame(AVFormatContext* avformatCtx);
  virtual uint64_t handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType);

  inline uint64_t addProcessor(MediaProcessor* vds) {
    if (vds == nullptr)
      return MAKE_P_STAT(NO_PROCESSOR_SET);
    mediaProcessors.push_back(vds);
    return 0;
  }

  inline uint64_t setStreamSelector(StreamSelector* pselector) {
    selector = pselector;
    return 0;
  }

};

}
} /* namespace pilecv4j */

#endif /* _MEDIAPROCESSORCHAIN_H_ */
