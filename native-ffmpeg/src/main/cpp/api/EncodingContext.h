
#ifndef _ENCODER_H_
#define _ENCODER_H_

#include "utils/IMakerManager.h"

#include <atomic>
#include <string>
#include <map>

extern "C" {
#include <libavformat/avformat.h>
}

#define DEFAULT_MAX_REMUX_ERRORS 20
#define DEFAULT_FPS 30

namespace pilecv4j
{

enum EncoderState {
  ENC_FRESH = 0,
  ENC_OPEN_CONTEXT,
  ENC_OPEN_STREAMS,
  ENC_READY,
  ENC_ENCODING,
  ENC_STOPPED
};

enum VideoEncoderState {
  VE_FRESH = 0,
  VE_SET_UP,
  VE_STOPPED
};

class EncodingContext;

/**
 * This class is NOT thread safe. ALL calls to the VideoEncoder and the EncodingContext
 * should be done from the same thread.
 */
class VideoEncoder {
  EncodingContext* enc;
  std::string video_codec;

  std::map<std::string,std::string> options;
  VideoEncoderState state = VE_FRESH;

  // ==================================
  // valid once state becomes VE_ENABLED
  AVCodec* video_avc = nullptr;
  AVStream* video_avs = nullptr;
  AVCodecContext* video_avcc = nullptr;
  // ==================================

  int32_t maxRemuxErrorCount = DEFAULT_MAX_REMUX_ERRORS;
  int64_t framecount = 0;

  IMakerManager::Transform xform;

  // ==================================
  // encoding parameters
  int fps = DEFAULT_FPS;
  int bufferSize = -1;
  int64_t minBitrate = -1;
  int64_t maxBitrate = -1;
  // ==================================

  AVFrame* frame = nullptr;
  AVPacket output_packet = {0};

  // ==================================
  // stupid hack
  uint8_t* streams_original_extradata = nullptr;
  int streams_original_extradata_size = 0;
  bool streams_original_set = false;
public:
  inline VideoEncoder(EncodingContext* penc, const char* pvideo_codec) : enc(penc), video_codec(pvideo_codec) { }
  ~VideoEncoder();

  uint64_t addCodecOption(const char* key, const char* val);

  inline uint64_t setFps(int pfps) {
    fps = pfps;
    return 0;
  }

  inline uint64_t setBufferSize(int pbufferSize) {
    bufferSize = pbufferSize;
    return 0;
  }

  inline uint64_t setBitrate(int64_t pminBitrate, int64_t pmaxBitrate = -1) {
    minBitrate = pminBitrate;
    maxBitrate = (pmaxBitrate < 0) ? minBitrate : pmaxBitrate;
    return 0;
  }

  inline uint64_t setEncodingParameters(int pfps, int pbufferSize, int64_t pminBitrate, int64_t pmaxBitrate = -1) {
    setFps(pfps);
    setBufferSize(pbufferSize);
    setBitrate(pminBitrate, pmaxBitrate);
    return 0;
  }

  uint64_t enable(uint64_t matRef, bool isRgb);

  uint64_t enable(bool isRgb, int width, int height, size_t stride = -1);

  uint64_t encode(uint64_t matRef, bool isRgb);

  inline uint64_t stop() {
    state = VE_STOPPED;
    return 0;
  }
};


/**
 * This class is NOT thread safe. ALL calls to the VideoEncoder and the EncodingContext
 * should be done from the same thread.
 */
class EncodingContext
{
  std::string fmt;
  bool fmtNull = false;
  std::string outputUri;

  AVFormatContext *output_format_context = nullptr;
  bool cleanupIoContext = false;
  EncoderState state = ENC_FRESH;

  bool wroteHeader = false;

  friend class VideoEncoder;

public:
  inline EncodingContext() = default;
  ~EncodingContext();

  uint64_t setupOutputContext(const char* lfmt, const char* loutputUri);

  inline VideoEncoder* openVideoEncoder(const char* video_codec) {
    return new VideoEncoder(this, video_codec);
  }

  uint64_t ready();

  uint64_t stop();
};

} /* namespace pilecv4j */

#endif /* _ENCODER_H_ */
