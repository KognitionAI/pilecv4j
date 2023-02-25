/*
 * DecodedFrameProcessor.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _DECODEDFRAMEPROCESSOR_H_
#define _DECODEDFRAMEPROCESSOR_H_

#include "api/MediaProcessor.h"

#include "utils/Synchronizer.h"

extern "C" {
#include <libswscale/swscale.h>
}

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * This one is what we push decoded frames to.
 */
typedef uint64_t (*push_frame)(uint64_t frame, int32_t isRgb, int32_t streamIndex);

struct CodecDetails;

/**
 * This is a media processor that decodes video frames and passes them to the callback
 * that's been provided to the constructor.
 */
class DecodedFrameProcessor: public MediaProcessor
{
  CodecDetails** codecs = nullptr;
  int32_t numStreams = -1;

  push_frame callback;

  std::string decoderName;
  bool decoderNameSet;

  uint64_t decode_packet(CodecDetails* pCodecContext, AVPacket *pPacket);
  uint64_t createMatFromFrame(AVFrame *pFrame, SwsContext** colorCvrt, int32_t& isRgb);

  AVPixelFormat requestedPixFormat = AV_PIX_FMT_RGB24;

  int maxDim;

public:
  inline DecodedFrameProcessor(push_frame pcallback, int pmaxDim, const char* pdecoderName) : callback(pcallback),
       decoderNameSet(pdecoderName ? true : false), maxDim(pmaxDim) {
    if (pdecoderName)
      decoderName = pdecoderName;
  }
  virtual ~DecodedFrameProcessor() = default;

  virtual uint64_t setup(PacketSourceInfo* psi, std::vector<std::tuple<std::string,std::string> >& options) override;
  virtual uint64_t handlePacket(AVPacket* pPacket, AVMediaType packetMediaType) override;

  virtual uint64_t close() override;

  inline void replace(push_frame pf) {
    callback = pf;
  }
};

}
} /* namespace pilecv4j */

#endif /* SRC_MAIN_CPP_DECODEDFRAMEPROCESSOR_H_ */
