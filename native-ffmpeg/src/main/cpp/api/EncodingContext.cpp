/*
 * Encoder.cpp
 *
 *  Created on: Jul 12, 2022
 *      Author: jim
 */

#include "api/EncodingContext.h"
#include "api/Muxer.h"
#include "utils/Synchronizer.h"
#include "common/FakeMutexGuard.h"
#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

#include "common/kog_exports.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavutil/opt.h>
}

#include <cmath>

using namespace ai::kognition::pilecv4j;

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "ECTX"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

EncodingContext::~EncodingContext()
{
  PILECV4J_TRACE;
  stop(true); // stop if not already stopped
}

uint64_t EncodingContext::stop(bool lock) {
  PILECV4J_TRACE;
  FakeMutextGuard g(fake_mutex, lock);

  if (state == ENC_STOPPED)
    return 0;

  state = ENC_STOPPED;
  if (muxer) {
    uint64_t iret;
    if (isError(iret = muxer->close()))
      return iret;
  }
  return 0;
}

uint64_t EncodingContext::setMuxer(Muxer* pmuxer) {
  PILECV4J_TRACE;
  if (state != ENC_FRESH) {
    llog(ERROR, "EncodingContext is in the wrong state to set the Muxer. It should have been ENC_FRESH(%d) but it's in %d.", (int)ENC_FRESH, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  if (muxer) {
    llog(ERROR, "Muxer is already set.");
    return MAKE_P_STAT(ALREADY_SET);
  }
  if (!pmuxer) {
    llog(ERROR, "Cannot set muxer on encoder to NULL");
    return MAKE_P_STAT(NO_OUTPUT);
  }
  muxer = pmuxer;
  return muxer->open();
}

uint64_t EncodingContext::ready(bool lock) {
  PILECV4J_TRACE;
  FakeMutextGuard g(fake_mutex, lock);

  if (encoders.size() == 0) {
    llog(ERROR, "No encoders were added to the context. There is nothing to do.");
    return MAKE_P_STAT(NO_SUPPORTED_CODEC);
  }

  if (!muxer) {
    llog(ERROR, "Cannot ready the encoder without setting a muxer");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  uint64_t iret = 0;
  if (isError(iret = muxer->ready())) {
    llog(ERROR, "Failed to ready the muxer");
    return iret;
  }

  for (auto ve : encoders) {
    if (isError(iret = ve->ready(false)))
      return iret;
  }

  state = ENC_READY;
  return 0;
}

uint64_t VideoEncoder::enable(bool lock, uint64_t matRef, bool isRgb) {
  PILECV4J_TRACE;
  auto imaker = IMakerManager::getIMaker();
  if (!imaker)
    return MAKE_P_STAT(NO_IMAGE_MAKER_SET);

  RawRaster details;
  if (!imaker->extractImageDetails(matRef, isRgb, &details))
    return MAKE_P_STAT(FAILED_CREATE_FRAME);

  return enable(lock, isRgb, details.w, details.h, details.stride, -1, -1);
}

static void calcOutputWidth(int width, int height, int outputWidth, int outputHeight, bool preserveAspectRatio, bool onlyScaleDown, int& outputWidthToUse, int& outputHeightToUse) {
  outputHeightToUse = -1;
  outputWidthToUse = -1;
  if ((outputWidth < 0 && outputHeight < 0) || outputWidth == 0 || outputHeight == 0) {
    if (outputWidth == 0 || outputHeight == 0)
      llog(WARN, "Can't scale the image if either the output width or height is set to zero. No scaling will take place.");
    return;
  }

  // we want to preserve the aspect ratio even if preserveAspectRatio is false as long
  // as one of the dimensions remains unset.
  bool par = preserveAspectRatio || outputHeight == -1 || outputWidth == -1;
  if (par) {
    double scaleWidth = outputWidth < 0 ? 1E10 : (double)outputWidth / (double)width;
    double scaleHeight = outputHeight < 0 ? 1E10 : (double)outputHeight / (double)height;
    double scale = std::min(scaleWidth, scaleHeight);
    bool widthBiased = scaleWidth < scaleHeight;
    if (scale > 1.0 && onlyScaleDown)
      return;
    outputWidthToUse = widthBiased ? outputWidth : (int)std::round((double)width * scale);
    outputHeightToUse = widthBiased ? (int)std::round((double)height * scale) : outputHeight;
  } else {
    outputWidthToUse = outputWidth;
    outputHeightToUse = outputHeight;
  }
}

uint64_t VideoEncoder::enable(bool lock, bool isRgb, int width, int height, int stride, int dstW, int dstH) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex, lock);

  int outputWidthToUse;
  int outputHeightToUse;
  calcOutputWidth(width, height, outputWidth, outputHeight, preserveAspectRatio, onlyScaleDown, outputWidthToUse, outputHeightToUse);
  if (dstW < 0)
    dstW = outputWidthToUse;
  if (dstW < 0)
    dstW = width;

  if (dstH < 0)
    dstH = outputHeightToUse;
  if (dstH < 0)
    dstH = height;

  if (state != VE_FRESH) {
    llog(ERROR, "VideoEncoder is in the wrong state. It should have been in VE_FRESH(%d) but it's in %d.", (int)VE_FRESH, (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  if (!enc->muxer) {
    llog(ERROR, "Cannot enable the encoder without setting a muxer to write the output to.");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  uint64_t result = 0;
  AVDictionary* opts = nullptr;
  int avres = 0;
  const enum AVPixelFormat* supported_formats = nullptr;
  AVCodecContext* video_avcc = nullptr;
  AVStream* video_st = nullptr;
  int video_sindex = -1;

  //llog(TRACE, "STEP 3: find codec");
  if (video_codec_isNull) {
    const AVOutputFormat* avof = enc->muxer->guessOutputFormat();
    if (!avof) {
      llog(ERROR, "Failed to guess output format");
      goto fail;
    }
    video_avc = avcodec_find_encoder(avof->video_codec);
  } else {
    video_avc = avcodec_find_encoder_by_name(video_codec.c_str());
  }

  if (!video_avc) {
    llog(ERROR, "Failed to find the codec");
    goto fail;
  }

  video_avcc = avcodec_alloc_context3(video_avc);
  if (!video_avcc) {
    llog(ERROR, "Failed to allocate the codec context");
    goto fail;
  }

  // Check if the codec supports the requested pixel format
  #if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 0, 0)
  if (avcodec_get_supported_config(video_avcc, video_avc, AV_CODEC_CONFIG_PIX_FORMAT, 0, (const void**)&supported_formats, nullptr) >= 0 && supported_formats) {
    video_avcc->pix_fmt = supported_formats[0]; // use the first one if there's one in the codec
  } else {
    video_avcc->pix_fmt = isRgb ? AV_PIX_FMT_RGB24 : AV_PIX_FMT_BGR24;
  }
  #else
  // For older FFmpeg versions, use a simpler approach
  if (video_avc->pix_fmts) {
    video_avcc->pix_fmt = video_avc->pix_fmts[0]; // use the first one if there's one in the codec
  } else {
    video_avcc->pix_fmt = isRgb ? AV_PIX_FMT_RGB24 : AV_PIX_FMT_BGR24;
  }
  #endif

  if (rcBufferSize >= 0) {
    llog(TRACE, "Encoder buffer size: %ld", (long) rcBufferSize);
    video_avcc->rc_buffer_size = rcBufferSize;
  }
  if (maxRcBitrate >= 0) {
    llog(TRACE, "Encoder rate-control max bit rate: %ld", (long) maxRcBitrate);
    video_avcc->rc_max_rate = maxRcBitrate;
  }
  if (minRcBitrate >= 0) {
    llog(TRACE, "Encoder rate-control min bit rate: %ld", (long) minRcBitrate);
    video_avcc->rc_min_rate = minRcBitrate;
  }
  if (bitrate >= 0) {
    llog(TRACE, "Encoder target bit rate: %ld", (long) bitrate);
    video_avcc->bit_rate = bitrate;
  }

  llog(TRACE, "Encoder frame rate: %d / %d", (int)framerate.num, (int)framerate.den);
  video_avcc->codec_tag = 0;
  video_avcc->codec_type = AVMEDIA_TYPE_VIDEO;
  video_avcc->width = dstW;
  video_avcc->height = dstH;

  video_avcc->time_base = av_inv_q(framerate);
  video_avcc->framerate = framerate;

  result = enc->muxer->createNextStream(video_avcc, &video_sindex);
  if (isError(result)) {
    llog(ERROR, "Failed to create stream in muxer");
    goto fail;
  }
  llog(TRACE, "video stream index %d", (int)video_sindex);

  if (enc->muxer->getFormatContext()->oformat->flags & AVFMT_GLOBALHEADER)
    video_avcc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

  result = buildOptions(options, &opts);
  if (isError(result))
    return result;
  avres = avcodec_open2(video_avcc, video_avc, &opts);
  result = MAKE_AV_STAT(avres);
  if (!isError(result) && options.size() > 0) {
    rebuildOptions(opts, options);
    logRemainingOptions(INFO, COMPONENT, "after opening the encoder", options);
  }
  if (opts != nullptr)
    av_dict_free(&opts);
  if (isError(result))
    goto fail;

  // ======================================
  // This is a weird hack discovered here: https://stackoverflow.com/questions/48578088/streaming-flv-to-rtmp-with-ffmpeg-using-h264-codec-and-c-api-to-flv-js
  // We're going to store off the original setting so we can put it back correctly.
  // If we don't do this then we get a double free if we close both the AVCodecContext
  // and the overall AVFormatContext
  {
    video_st = enc->muxer->getStream(video_sindex);
    if (!video_st) {
      result = MAKE_P_STAT(NO_STREAM);
      goto fail;
    }

    streams_original_extradata = video_st->codecpar->extradata;
    streams_original_extradata_size = video_st->codecpar->extradata_size;
    video_st->codecpar->extradata = video_avcc->extradata;
    video_st->codecpar->extradata_size = video_avcc->extradata_size;
    streams_original_set = true;
  }
  // ======================================

  result = IMakerManager::setupTransform(width, height, stride, isRgb ? ai::kognition::pilecv4j::RGB24 : ai::kognition::pilecv4j::BGR24, video_avcc, dstW, dstH, &xform);
  inputWillBeRgb = isRgb;

  if (isError(result)) {
    llog(ERROR, "Failed to setup transform");
    goto fail;
  }

  state = VE_ENABLED;

  return 0;
  fail:
  if (video_avcc) {
    avcodec_free_context(&video_avcc);
    video_avcc = nullptr;
  }
  enc->muxer->fail();
  return result;
}

uint64_t VideoEncoder::streaming(bool lock) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex, lock);

  // this should NOT already be encoding.
  if (state >= VE_ENCODING) {
    llog(ERROR, "VideoEncoder is in the wrong state. streaming() must be called before encoding. The current state is %d.", (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  sync = new Synchronizer();
  return 0;
}

uint64_t VideoEncoder::ready(bool lock) {
  FakeMutextGuard g(enc->fake_mutex, lock);

  AVStream* stream = enc->muxer->getStream(video_sindex);
  if (!stream) {
    llog(ERROR, "The muxer doesn't appear to have a stream at index %d. Was the encoder enabled?", (int)video_sindex);
    if (enc->encoders.size() > 1)
      llog(ERROR, "   When you have multiple encoders in an encoding context you need to enable them all before using any of them to encode.", (int)video_sindex);
    return MAKE_P_STAT(NO_STREAM);
  }

  video_stime_base = stream->time_base;
  return 0;
}

uint64_t VideoEncoder::encode(bool lock, uint64_t matRef, bool isRgb) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex, lock);

  if (!matRef) {
    llog(WARN, "null mat passed to encode. Ignoring");
    return 0;
  }

  uint64_t result = 0;

  // this should usually be in the VE_ENCODING state except on the first call so we want to check for that first.
  if (state != VE_ENCODING) {
    if (state == VE_FRESH) {
      // this should move the state to VE_ENABLED
      if (isError(result = enable(false, matRef, isRgb))) {
        llog(ERROR, "Failed to auto-enable the video encoder.");
        return result;
      }
    }

    if (state == VE_ENABLED) {
      state = VE_ENCODING;
      if (sync)
        sync->start();
    } else {
      llog(ERROR, "VideoEncoder is in the wrong state. It should have been in VE_ENABLED(%d) or VE_ENCODING(%d) but it's in %d.", (int)VE_ENABLED, (int)VE_ENCODING, (int)state);
      return MAKE_P_STAT(BAD_STATE);
    }
  }

  if (enc->state < ENC_READY) {
    if (isError(result = enc->ready(false))) {
      llog(ERROR, "Failed to ready the encoding context prior to encoding.");
      return result;
    }
  }

  if (enc->state != ENC_READY) {
    llog(ERROR, "EncodingContext is in the wrong state. It should have been in ENC_READY(%d) but it's in %d.", (int)ENC_READY, (int)enc->state);
    return MAKE_P_STAT(BAD_STATE);
  }

  int rc = 0;

  llog(TRACE, "Creating frame from mat at %" PRId64, matRef);
  result = IMakerManager::createFrameFromMat(&xform, matRef, isRgb, video_avcc, &frame);
  if (isError(result)) {
    llog(TRACE, "Failed creating frame from mat at %" PRId64 " : (%d : %s).", matRef, rc, av_err2str(rc));
    return result;
  }
  llog(TRACE, "Created frame at %" PRId64 " from mat at %" PRId64, (uint64_t)frame, matRef);

  // ==================================================================
  // encode the frame
  if (sync) {
    int64_t pts;
    int64_t oneInterval = av_rescale_q(1, video_avcc->time_base, video_stime_base);
    do {
      pts = framecount * oneInterval;
      framecount++;
    } while(sync->throttle(pts, video_stime_base));
    frame->pts = pts - oneInterval;
  } else {
    llog(TRACE, "rescaling pts for frame at %" PRId64, (uint64_t)frame);
    frame->pts = framecount * av_rescale_q(1, video_avcc->time_base, video_stime_base);
    framecount++;
  }

  // Initialize the output packet
  AVPacket* output_packet = av_packet_alloc();
  if (!output_packet) {
    llog(ERROR, "Failed to allocate output packet");
    return MAKE_P_STAT(FAILED_CREATE_PACKET);
  }

  for (bool frameSent = false; ! frameSent; ) {
    llog(TRACE, "avcodec_send_frame sending frame at %" PRId64, (uint64_t) frame);
    rc = avcodec_send_frame(video_avcc, frame);
    if (rc == AVERROR(EAGAIN)) {
      llog(TRACE, "avcodec_send_frame not sent.: (%d : %s). Will try again", rc, av_err2str(rc));
      rc = 0;
    } else {
      if (rc < 0) {
        llog(ERROR,"Error while sending frame: %d, %s", (int)rc, av_err2str(rc));
        return MAKE_AV_STAT(rc);
      }

      llog(TRACE, "avcodec_send_frame sent successfully", rc, av_err2str(rc));
      // we didn't get an EAGAIN so we can leave this loop
      // once the packet is received
      frameSent = true;
    }

    bool packetReceived = false;
    while (rc >= 0) {
      rc = avcodec_receive_packet(video_avcc, output_packet);
      if (rc == AVERROR(EAGAIN) || rc == AVERROR_EOF) {
        if (isEnabled(TRACE))
          llog(TRACE, "avcodec_receive_packet needs more info: %d : %s", rc, av_err2str(rc));
        // this isn't considered an error
        result = 0;
        break;
      } else if (rc < 0) {
        llog(ERROR,"Error while receiving packet from encoder: %d, %s", (int)rc, av_err2str(rc));
        result = MAKE_AV_STAT(rc);
        break;
      } else if (isEnabled(TRACE))
        llog(TRACE, "avcodec_receive_packet - packet received.");

      packetReceived = true;

      output_packet->stream_index = video_sindex;

      if (isEnabled(TRACE)) {
        llog(TRACE, "Output Packet Timing[stream %d]: pts/dts: [ %" PRId64 "/ %" PRId64 " ] duration: %" PRId64 " timebase: [ %d / %d ]",
            (int) output_packet->stream_index,
            (int64_t)output_packet->pts, (int64_t)output_packet->dts,
            (int64_t)output_packet->duration,
            (int)video_stime_base.num, (int)video_stime_base.den);
      }

      enc->muxer->writeFinalPacket(output_packet);
    }

    if (packetReceived)
      av_packet_unref(output_packet);
  }
  // ==================================================================

  // Clean up
  av_packet_unref(output_packet);
  av_packet_free(&output_packet);

  return result;
}

uint64_t VideoEncoder::addCodecOption(const char* key, const char* val) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex, true);

  if (state != VE_FRESH) {
    llog(ERROR, "VideoEncoder is in the wrong state (%d). You cannot add codec options after it's been enabled.", (int)state);
    return MAKE_P_STAT(BAD_STATE);
  }

  if (options.find(key) != options.end())
    // then the key is already set.
    return MAKE_P_STAT(OPTION_ALREADY_SET);
  options[key] = val;
  return 0;
}

uint64_t VideoEncoder::stop(bool lock) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex, lock);

  // need to put it back or we get a double free when closing the overall context
  AVStream* video_st = enc->muxer->getStream(video_sindex);
  if (streams_original_set && video_st) {
    if (isEnabled(TRACE))
      llog(TRACE, "Resetting video_st(%" PRId64 ")->codecpar(%" PRId64 ")->extradata(%" PRId64 ") to %" PRId64,
          (uint64_t)video_st, (uint64_t)(video_st ? video_st->codecpar : 0L),
          (uint64_t)((video_st && video_st->codecpar) ? video_st->codecpar->extradata : 0L),
          (uint64_t)streams_original_set);
    video_st->codecpar->extradata = streams_original_extradata;
    video_st->codecpar->extradata_size = streams_original_extradata_size;
    streams_original_set = false;
  }

  state = VE_STOPPED;
  return 0;
}

VideoEncoder::~VideoEncoder() {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex, true);
  stop(false); // stop if not already stopped

  if (frame) {
    llog(TRACE, "Freeing frame at %" PRId64, (uint64_t)frame );
    IMakerManager::freeFrame(&frame);
  }

  if (video_avcc) {
    if (isEnabled(TRACE))
      llog(TRACE, "freeing video_avcc at %" PRId64, (uint64_t)video_avcc);
    avcodec_free_context(&video_avcc);
  }
}

extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_encodingContext_create() {
  PILECV4J_TRACE;
  uint64_t ret = (uint64_t)new EncodingContext();
  if (isEnabled(TRACE))
    llog(TRACE, "Creating new EncodingContext: %" PRId64, ret);
  return ret;
}

KAI_EXPORT void pcv4j_ffmpeg2_encodingContext_delete(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Deleting EncodingContext: %" PRId64, nativeDef);
  EncodingContext* enc = (EncodingContext*)nativeDef;
  delete enc;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_encodingContext_setMuxer(uint64_t nativeDef, uint64_t muxerRef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Setting up muxer for EncodingContext: %" PRId64, nativeDef);
  EncodingContext* enc = (EncodingContext*)nativeDef;
  Muxer* muxer = (Muxer*)muxerRef;
  return enc->setMuxer(muxer);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_encodingContext_openVideoEncoder(uint64_t nativeDef, const char* video_codec) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "opening Video Encoder:on EncodingContext %" PRId64, nativeDef);
  EncodingContext* enc = (EncodingContext*)nativeDef;
  uint64_t ret = (uint64_t)enc->openVideoEncoder(video_codec);
  if (isEnabled(TRACE))
    llog(TRACE, "Opened new Video Encoder: %" PRId64, ret);
  return ret;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_encodingContext_ready(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Readying EncodingContext %" PRId64, nativeDef);
  EncodingContext* enc = (EncodingContext*)nativeDef;
  uint64_t ret = (uint64_t)enc->ready(true);
  return ret;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_encodingContext_stop(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Stopping EncodingContext %" PRId64, nativeDef);
  EncodingContext* enc = (EncodingContext*)nativeDef;
  uint64_t ret = (uint64_t)enc->stop(true);
  return ret;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_encode(uint64_t nativeDef, uint64_t matRef, int32_t isRgb) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Encoding mat at: %" PRId64 " as frame using video encoder at %" PRId64, matRef, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->encode(true, matRef, isRgb ? true : false);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_addCodecOption(uint64_t nativeDef, const char* key, const char* val) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "adding option for video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->addCodecOption(key,val);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setFramerate(uint64_t nativeDef, int32_t pfps_num, int32_t pfps_den) {
  if (isEnabled(TRACE))
    llog(TRACE, "setting fps for video encoder at: %" PRId64 ": fps: %d/%d",  nativeDef, (int)pfps_num, (int)pfps_den);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  AVRational framerate = { pfps_num, pfps_den };
  return enc->setFramerate(framerate);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setOutputDims(uint64_t nativeDef, int32_t width, int32_t height, int32_t preserveAspectRatio, int32_t onlyScaleDown) {
  if (isEnabled(TRACE))
    llog(TRACE, "setting output dims for video encoder at: %" PRId64 ": %d x %d",  nativeDef, (int)width, (int)height);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setOutputDims(width, height, preserveAspectRatio ? true : false, onlyScaleDown ? true : false);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setRcBufferSize(uint64_t nativeDef, int32_t pbufferSize) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting buffer size for video encoder at: %" PRId64 ": bufferSize: %d",  nativeDef, (int)pbufferSize);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setRcBufferSize(pbufferSize);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setRcBitrate(uint64_t nativeDef, int64_t pminBitrate, int64_t pmaxBitrate) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting rate-control bitrate for video encoder at: %" PRId64 ": min bitrate: %ld, max bitrate: %ld",
        nativeDef, (long)pminBitrate, (long)pmaxBitrate);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setRcBitrate(pminBitrate, pmaxBitrate);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setTargetBitrate(uint64_t nativeDef, int64_t pbitrate) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting target bitrate for video encoder at: %" PRId64 " to: %ld", nativeDef, (long)pbitrate);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setTargetBitrate(pbitrate);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable(uint64_t nativeDef, int32_t isRgb, int32_t width, int32_t height, int32_t stride, int32_t dstW, int32_t dstH) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable(true, (bool)(isRgb ? true : false), width, height, stride, dstW, dstH);
}

KAI_EXPORT void pcv4j_ffmpeg2_videoEncoder_delete(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "deleting video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  delete enc;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_stop(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Stopping video encoder at %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->stop(true);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_streaming(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Stopping video encoder at %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->streaming(true);
}

} /* extern "C" */
}

} /* namespace pilecv4j */

