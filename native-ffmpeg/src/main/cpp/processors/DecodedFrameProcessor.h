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

  uint64_t decode_packet(CodecDetails* pCodecContext, AVPacket *pPacket);
  uint64_t createMatFromFrame(AVFrame *pFrame, SwsContext** colorCvrt, int32_t& isRgb);

public:
  inline DecodedFrameProcessor(push_frame pcallback) : callback(pcallback) {}
  virtual ~DecodedFrameProcessor() = default;

  virtual uint64_t setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams);
  virtual uint64_t preFirstFrame(AVFormatContext* avformatCtx);
  virtual uint64_t handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType packetMediaType);

  virtual uint64_t close();
};

}
} /* namespace pilecv4j */

#endif /* SRC_MAIN_CPP_DECODEDFRAMEPROCESSOR_H_ */
