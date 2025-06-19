/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#include <muxers/SegmentedMuxer.h>

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

#include "common/kog_exports.h"

extern "C" {
#include <libswscale/swscale.h>
}

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "SMUX"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

class StreamCreator {
public:
  virtual ~StreamCreator() = default;
  virtual uint64_t createNextStream(Muxer*, int* stream_index_out) = 0;
};

SegmentedMuxer::~SegmentedMuxer() {
  PILECV4J_TRACE;
  if (!closed)
    close();
}

void SegmentedMuxer::fail() {
  PILECV4J_TRACE;
}

uint64_t SegmentedMuxer::close() {
  PILECV4J_TRACE;
  if (!closed) {
    if (currentMuxer)
      currentMuxer->close();
    currentMuxer = nullptr;
    for (auto sc : streamCreators) {
      delete sc;
    }
    streamCreators.clear();
    closed = true;
    return 0;
  }
  return 0;
}

uint64_t SegmentedMuxer::open() {
  PILECV4J_TRACE;

  if (!currentMuxer) {
    uint64_t result = muxerSupplier(muxCount++, &currentMuxer);
    if (isError(result)) {
      currentMuxer = nullptr;
      llog(ERROR,"Failed to create the first muxer.");
      return result;
    }
  }

  if (!currentMuxer) {
    llog(ERROR, "Failed to create muxer for the first segment.");
    return MAKE_P_STAT(FAILED_CREATE_MUXER);
  }

  return currentMuxer->open();
}

uint64_t SegmentedMuxer::ready() {
  PILECV4J_TRACE;
  // ready the first muxer
  if (!currentMuxer) {
    llog(ERROR, "SegmentedMuxer doesn't appear to have been opened successfully");
    return MAKE_P_STAT(BAD_STATE);
  }
  return currentMuxer->ready();
}

class AVCPStreamCreator : public StreamCreator {
  AVCodecParameters* codecPars;

public:
  inline AVCPStreamCreator(AVCodecParameters* pcodecPars) : codecPars(pcodecPars) {}
  virtual inline ~AVCPStreamCreator() {
    if (codecPars)
      avcodec_parameters_free(&codecPars);
  }

  virtual inline uint64_t createNextStream(Muxer* curMuxer, int* stream_index_out) {
    return curMuxer->createNextStream(codecPars, stream_index_out);
  }
};

AVFormatContext* SegmentedMuxer::getFormatContext() {
  return currentMuxer ? currentMuxer->getFormatContext() : nullptr;
}

uint64_t SegmentedMuxer::createNextStream(AVCodecParameters* codecPars, int* stream_index_out) {
  PILECV4J_TRACE;

  if (!currentMuxer) {
    llog(ERROR, "SegmentedMuxer doesn't appear to have been opened successfully");
    return MAKE_P_STAT(BAD_STATE);
  }

  AVCodecParameters * params = avcodec_parameters_alloc();
  if (!params) {
    llog(ERROR,"Failed to allocate AVCodecParameters");
    return MAKE_AV_STAT(AVERROR(ENOMEM));
  }

  int rc = avcodec_parameters_copy(params, codecPars);
  if (rc < 0) {
    llog(ERROR, "Couldn't copy codec parameters: %s", av_err2str(rc));
    return MAKE_AV_STAT(rc);
  }

  int sindex = 0;
  auto curSc = new AVCPStreamCreator(params);
  auto ret = curSc->createNextStream(currentMuxer, &sindex);
  if (isError(ret)) {
    llog(ERROR, "Failed to create stream.");
    return ret;
  }
  if (stream_index_out)
    *stream_index_out = sindex;

  // the stream index SHOULD be the same as the index into the vectors.
  if (sindex != streamCreators.size())
    llog(WARN, "The stream index returned from creating a new stream doesn't appear to be sequential. Expected %d but got %d",
       (int)streamCreators.size(), (int)sindex);

  streamCreators.push_back(curSc);
  streamMediaTypes.push_back(params->codec_type);
  if (reference_stream < 0 && params->codec_type == AVMEDIA_TYPE_VIDEO)
    reference_stream = sindex;

  return 0;
}

static inline bool isKeyFrame(const AVPacket* packet) {
  return (packet->flags & AV_PKT_FLAG_KEY)? true : false;
}

uint64_t SegmentedMuxer::createNextStream(const AVCodecContext* codecc, int* stream_index_out) {
  PILECV4J_TRACE;
  AVCodecParameters * params = avcodec_parameters_alloc();
  if (!params) {
    llog(ERROR,"Failed to allocate AVCodecParameters");
    return MAKE_AV_STAT(AVERROR(ENOMEM));
  }

  int rc = avcodec_parameters_from_context(params, codecc);
  if (rc < 0) {
    llog(ERROR, "Couldn't copy codec parameters: %s", av_err2str(rc));
    return MAKE_AV_STAT(rc);
  }

  auto curSc = new AVCPStreamCreator(params);
  streamMediaTypes.push_back(params->codec_type);
  streamCreators.push_back(curSc);
  int sindex = -1;
  auto ret = curSc->createNextStream(currentMuxer, &sindex);
  if (reference_stream < 0 && params->codec_type == AVMEDIA_TYPE_VIDEO && !isError(ret) )
    reference_stream = sindex;
  if (stream_index_out)
    *stream_index_out = sindex;
  return ret;
}

uint64_t SegmentedMuxer::rotate() {
  uint64_t iret;
  if (isError(iret = currentMuxer->close())) {
    llog(ERROR, "Failed to close the current underlying muxer: %" PRId64 ", %s", (uint64_t)iret, errMessage(iret));
  }

  delete currentMuxer;
  currentMuxer = nullptr;
  if (isError(iret = muxerSupplier(muxCount++, &currentMuxer))) {
    llog(ERROR, "Failed to create the next muxer, number %" PRId64, (uint64_t)muxCount);
    return iret;
  }

  if (!currentMuxer) {
    llog(ERROR, "Failed to create the next muxer, number %" PRId64 " but there was no error", (uint64_t)muxCount);
    return MAKE_P_STAT(NO_OUTPUT);
  }

  if (isError(iret = currentMuxer->open())) {
    llog(ERROR, "Failed to create open the next muxer, number %" PRId64, (uint64_t)muxCount);
    return iret;
  }

  int stream_index = 0;
  for (auto sc : streamCreators) {
    if (isError(iret = sc->createNextStream(currentMuxer, &stream_index))) {
      llog(ERROR, "Failed to recreate the stream at %d", (int)stream_index);
      return iret;
    }
    AVMediaType omedType = streamMediaTypes[stream_index];
    AVStream* newStream = getStream(stream_index);
    if (!newStream) {
      llog(ERROR, "Attempt to retrieve newly created stream failed");
      return MAKE_P_STAT(NO_STREAM);
    }
    AVMediaType newMediaType;
    if (newStream->codecpar) {
      newMediaType = newStream->codecpar->codec_type;
      // verify nothing has changed.
      if (omedType != newMediaType) {
        llog(WARN, "The media type for stream number %d seems to have changed from %d to %d",
            (int)stream_index, (int)omedType, (int)newMediaType);
        streamMediaTypes[stream_index] = newMediaType;
      }
    } else
      llog(WARN, "Can't retrieve media type from new stream");
    stream_index++;
  }

  if (isError(iret = currentMuxer->ready())) {
    llog(ERROR, "Failed to create ready the next muxer, number %" PRId64, (uint64_t)muxCount);
    return iret;
  }

  return 0;
}

const AVOutputFormat* SegmentedMuxer::guessOutputFormat() {
  if (!currentMuxer) {
    uint64_t result = muxerSupplier(muxCount++, &currentMuxer);
    if (isError(result)) {
      currentMuxer = nullptr;
      llog(ERROR,"Failed to create the first muxer.");
      return nullptr;
    }
  }

  return currentMuxer ? currentMuxer->guessOutputFormat() : nullptr;
}

//uint64_t SegmentedMuxer::writePacket(AVPacket* outputPacket) {
//  uint64_t iret;
//  int stream_index = outputPacket->stream_index;
//  if (stream_index >= streamCreators.size()) {
//    llog(ERROR, "Received a packet for a stream at %d that doesn't exist.", (int)stream_index);
//    return MAKE_P_STAT(NO_STREAM);
//  }
//
//  AVStream* stream = getStream(stream_index);
//  if (!stream) {
//    llog(ERROR, "Received a packet for a stream at %d that's null.", (int)stream_index);
//    return MAKE_P_STAT(NO_STREAM);
//  }
//
//  if (!pendingClose && stream_index == reference_stream)
//    pendingClose = (closeSeg)(outputPacket, streamMediaTypes[stream_index], stream->time_base);
//
//  if (pendingClose && stream_index == reference_stream && isKeyFrame(outputPacket)) {
//    if (isError(iret = rotate()))
//      return iret;
//    pendingClose = false; // reset
//  }
//
//  return Muxer::writePacket(outputPacket);
//}

uint64_t SegmentedMuxer::writePacket(const AVPacket* inputPacket, const AVRational& inputPacketTimeBase, int output_stream_index) {
  uint64_t iret;
  int stream_index = output_stream_index;
  if (stream_index >= streamCreators.size()) {
    llog(ERROR, "Received a packet for a stream at %d that doesn't exist.", (int)stream_index);
    return MAKE_P_STAT(NO_STREAM);
  }

  AVStream* stream = getStream(stream_index);
  if (!stream) {
    llog(ERROR, "Received a packet for a stream at %d that's null.", (int)stream_index);
    return MAKE_P_STAT(NO_STREAM);
  }

  if (!pendingClose && stream_index == reference_stream)
    pendingClose = (closeSeg)(inputPacket, streamMediaTypes[stream_index], stream->time_base);

  if (pendingClose && stream_index == reference_stream && isKeyFrame(inputPacket)) {
    if (isError(iret = rotate()))
      return iret;
    pendingClose = false; // reset
  }

  return Muxer::writePacket(inputPacket, inputPacketTimeBase, output_stream_index);
  //return currentMuxer->writePacket(inputPacket, inputPacketTimeBase, output_stream_index);
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================

// return status
typedef uint64_t (*create_muxer_from_java)(uint64_t muxerNumber, uint64_t* muxerOut);
typedef int32_t (*should_close_segment)(int32_t mediaType, int32_t stream_index, int32_t packetNumBytes,
    int32_t isKeyFrame, int64_t pts, int64_t dts, int32_t tbNum, int32_t tbDen);

extern "C" {
  KAI_EXPORT uint64_t pcv4j_ffmpeg2_segmentedMuxer_create(const create_muxer_from_java create_muxer_callback, const should_close_segment ssc_callback) {
    PILECV4J_TRACE;

    Muxer* ret = new SegmentedMuxer(

        [create_muxer_callback](uint64_t muxerNumber, Muxer** muxerOut) {
      uint64_t muxer = 0L;
      uint64_t iret;

      if (!muxerOut) {
        llog(ERROR, "Out muxer parameter can't be null");
        return MAKE_P_STAT(NO_OUTPUT);
      }
      *muxerOut = nullptr;
      if (isError(iret = (*create_muxer_callback)(muxerNumber, &muxer))) {
        llog(ERROR, "Callback failed to instantiate a Muxer");
        return iret;
      }
      if (!muxer) {
        llog(ERROR, "Callback failed to instantiate a Muxer but didn't return an error");
        return MAKE_P_STAT(NO_OUTPUT);
      }
      *muxerOut = (Muxer*)muxer;
      return (uint64_t)0L;
    },

    [ssc_callback](const AVPacket* packet, const AVMediaType mediaType, const AVRational& tb) {
      return (*ssc_callback)(mediaType, packet->stream_index, packet->size, isKeyFrame(packet) ? 1 : 0,
          packet->pts, packet->dts, tb.num, tb.den) ? true : false;
    });

    return (uint64_t)ret;
  }
}

}
} /* namespace pilecv4j */
