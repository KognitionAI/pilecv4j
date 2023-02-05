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

using namespace ai::kognition::pilecv4j;

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "ENCC"
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
  stop(); // stop if not already stopped
}

uint64_t EncodingContext::stop() {
  PILECV4J_TRACE;
  FakeMutextGuard g(fake_mutex);

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
    llog(ERROR, "EncodingContext is in the wrong state to set the Muxer. It should have been FRESH but it's in %d.", (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
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
  uint64_t ret = muxer->open();
  if (!isError(ret))
    state = ENC_OPEN_CONTEXT;
  return ret;
}

uint64_t EncodingContext::ready() {
  PILECV4J_TRACE;
  FakeMutextGuard g(fake_mutex);

  if (state != ENC_OPEN_STREAMS) {
    llog(ERROR, "EncodingContext is in the wrong state. It should have been in %d but it's in %d.", (int)ENC_OPEN_STREAMS, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
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

  state = ENC_READY;
  return 0;
}

uint64_t VideoEncoder::enable(uint64_t matRef, bool isRgb, int dstW, int dstH) {
  PILECV4J_TRACE;
  auto imaker = IMakerManager::getIMaker();
  if (!imaker)
    return MAKE_P_STAT(NO_IMAGE_MAKER_SET);

  RawRaster details;
  if (!imaker->extractImageDetails(matRef, isRgb, &details))
    return MAKE_P_STAT(FAILED_CREATE_FRAME);

  return enable(isRgb, details.w, details.h, details.stride, dstW, dstH);
}

uint64_t VideoEncoder::enable(bool isRgb, int width, int height, size_t stride, int dstW, int dstH) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex);

  AVRational framerate = { fps, 1 };

  if (dstW < 0)
    dstW = width;

  if (dstH < 0)
    dstH = height;

  // the encoding context state needs to be open
  if (enc->state != ENC_OPEN_CONTEXT && enc->state != ENC_OPEN_STREAMS) {
    llog(ERROR, "EncodingContext is in the wrong state. It should have been in %d but it's in %d.", (int)ENC_OPEN_CONTEXT, (int)enc->state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (state != VE_FRESH) {
    llog(ERROR, "VideoEncoder is in the wrong state. It should have been in %d but it's in %d.", (int)VE_FRESH, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (!enc->muxer) {
    llog(ERROR, "Cannot enable the encoder without setting a muxer");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  uint64_t result = 0;
  AVDictionary* opts = nullptr;

  // llog(TRACE, "STEP 6: set_codec_params");

  // set the options
  result = buildOptions(options, &opts);
  if (isError(result)) {
    if (opts != nullptr)
      av_dict_free(&opts);
    return result;
  }

  int avres = 0;

  //llog(TRACE, "STEP 3: find codec");
  video_avc = avcodec_find_encoder_by_name(video_codec.c_str());
  if (!video_avc) {
    llog(ERROR, "could not find the proper codec");
    result = MAKE_P_STAT(FAILED_CREATE_CODEC);
    goto fail;
  }
  llog(TRACE, "video codec id %d: %s", (int)video_avc->id, PO(video_avc->name));

  //llog(TRACE, "STEP 4: avformat_new_stream ( ctx, codec (%d == %d)", (int)video_avc->id, (int)AV_CODEC_ID_H264 );
  //  avformat_new_stream(enc->output_format_context, video_avc);
  video_avs = nullptr;
  result = enc->muxer->createNextStream(video_avc, &video_avs);
  if (isError(result)) {
    llog(ERROR, "Failed to create stream in muxer");
    goto fail;
  }
  llog(TRACE, "video stream index %d", (int)video_avs->index);

  //llog(TRACE, "STEP 5: avcodec_alloc_context3 ( codec (%d == %d)", (int)video_avc->id, (int)AV_CODEC_ID_H264 );
  video_avcc = avcodec_alloc_context3(video_avc);
  if (!video_avcc) {
    llog(ERROR,"could not allocated memory for codec context");
    result = MAKE_P_STAT(FAILED_CREATE_CODEC_CONTEXT);
    goto fail;
  }

  if (bufferSize >= 0) {
    llog(TRACE, "Encoder buffer size: %ld", (long) bufferSize);
    video_avcc->rc_buffer_size = bufferSize;
  }
  if (maxBitrate >= 0) {
    llog(TRACE, "Encoder max bit rate: %ld", (long) maxBitrate);
    video_avcc->rc_max_rate = maxBitrate;
  }
  if (minBitrate >= 0) {
    llog(TRACE, "Encoder min bit rate: %ld", (long) minBitrate);
    video_avcc->rc_min_rate = minBitrate;
  }
  if (minBitrate >= 0 && maxBitrate == minBitrate)
    video_avcc->bit_rate = minBitrate;

  llog(TRACE, "Encoder frame rate: %d / %d", (int)framerate.num, (int)framerate.den);
  video_avcc->codec_tag = 0;
  video_avcc->codec_type = AVMEDIA_TYPE_VIDEO;
  video_avcc->width = dstW;
  video_avcc->height = dstH;
  // set with addCodecOption("g","12")
//  video_avcc->gop_size = 12;
  video_avcc->time_base = av_inv_q(framerate);
  video_avcc->framerate = framerate;
  video_avs->time_base = video_avcc->time_base;

  // video_avcc->sample_aspect_ratio = 0; // TODO: carry this over from the input: decoder_ctx->sample_aspect_ratio;
  if (video_avc->pix_fmts)
    video_avcc->pix_fmt = video_avc->pix_fmts[0]; // use the first one if there's one in the codec
  else
    video_avcc->pix_fmt = isRgb ? AV_PIX_FMT_RGB24 : AV_PIX_FMT_BGR24;

  if (enc->muxer->getFormatContext()->oformat->flags & AVFMT_GLOBALHEADER)
    video_avcc->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

  enc->state = ENC_OPEN_STREAMS;

  //llog(TRACE, "STEP 7: avcodec_parameters_from_context");

  result = MAKE_AV_STAT(avcodec_parameters_from_context(video_avs->codecpar, video_avcc));
  if (isError(result)) {
    llog(ERROR, "could not fill codec parameters");
    goto fail;
  }

  //llog(TRACE, "STEP 8: avcodec_open2");

  avres = avcodec_open2(video_avcc, video_avc, &opts);
  result = MAKE_AV_STAT(avres);
  if (opts != nullptr)
    av_dict_free(&opts);
  if (isError(result)) {
    if (avres == AVERROR(EINVAL))
      llog(ERROR, "There was a bad codec option set.");
    else
      llog(ERROR, "could not open the codec");
    goto fail;
  }

  // ======================================
  // This is a weird hack discovered here: https://stackoverflow.com/questions/48578088/streaming-flv-to-rtmp-with-ffmpeg-using-h264-codec-and-c-api-to-flv-js
  // We're going to store off the original setting so we can put it back correctly.
  // If we don't do this then we get a double free if we close both the AVCodecContext
  // and the overall AVFormatContext
  streams_original_extradata = video_avs->codecpar->extradata;
  streams_original_extradata_size = video_avs->codecpar->extradata_size;
  video_avs->codecpar->extradata = video_avcc->extradata;
  video_avs->codecpar->extradata_size = video_avcc->extradata_size;
  streams_original_set = true;
  // ======================================

  result = IMakerManager::setupTransform(width, height, stride, isRgb ? ai::kognition::pilecv4j::RGB24 : ai::kognition::pilecv4j::BGR24, video_avcc, dstW, dstH, &xform);
  if (isError(result)) {
    llog(ERROR, "Failed to setup transform");
    goto fail;
  }

  state = VE_SET_UP;

  return 0;
  fail:
  if (video_avcc) {
    avcodec_free_context(&video_avcc);
    video_avcc = nullptr;
  }
  enc->muxer->fail();
  return result;
}

uint64_t VideoEncoder::streaming() {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex);

  // this should NOT already be encoding.
  if (state >= VE_ENCODING) {
    llog(ERROR, "VideoEncoder is in the wrong state. streaming() must be called before encoding. The current state is %d.", (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  sync = new Synchronizer();
  return 0;
}

uint64_t VideoEncoder::encode(uint64_t matRef, bool isRgb) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex);

  if (!matRef) {
    llog(WARN, "null mat passed to encode. Ignoring");
    return 0;
  }

  if (enc->state != ENC_READY) {
    llog(ERROR, "EncodingContext is in the wrong state. It should have been in ENC_READY(%d) but it's in %d.", (int)ENC_READY, (int)enc->state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  uint64_t result = 0;
  int rc = 0;

  // this should usually be in the VE_ENCODING state except on the first call so we want to check for that first.
  if (state != VE_ENCODING) {
    if (state == VE_SET_UP) {
      state = VE_ENCODING;
      if (sync)
        sync->start();
    } else {
      llog(ERROR, "VideoEncoder is in the wrong state. It should have been in %d or %d but it's in %d.", (int)VE_SET_UP, (int)VE_ENCODING, (int)state);
      return MAKE_P_STAT(STREAM_BAD_STATE);
    }
  }

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
    int64_t oneInterval = av_rescale_q(1, video_avcc->time_base, video_avs->time_base);
    do {
      pts = framecount * oneInterval;
      framecount++;
    } while(sync->throttle(pts, video_avs->time_base));
    frame->pts = pts - oneInterval;
  } else {
    llog(TRACE, "rescaling pts for frame at %" PRId64, (uint64_t)frame);
    frame->pts = framecount * av_rescale_q(1, video_avcc->time_base, video_avs->time_base);
    framecount++;
  }

  av_init_packet(&output_packet);

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
      rc = avcodec_receive_packet(video_avcc, &output_packet);
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

      output_packet.stream_index = video_avs->index;

      if (isEnabled(TRACE)) {
        llog(TRACE, "Output Packet Timing[stream %d]: pts/dts: [ %" PRId64 "/ %" PRId64 " ] duration: %" PRId64 " timebase: [ %d / %d ]",
            (int) output_packet.stream_index,
            (int64_t)output_packet.pts, (int64_t)output_packet.dts,
            (int64_t)output_packet.duration,
            (int)video_avs->time_base.num, (int)video_avs->time_base.den);
      }

      rc = av_interleaved_write_frame(enc->muxer->getFormatContext(), &output_packet);
      if (rc != 0) {
        llog(ERROR,"Error %d while writing packet to output: %s", rc, av_err2str(rc));
        result = MAKE_AV_STAT(rc);
      }
    }

    if (packetReceived)
      av_packet_unref(&output_packet);
  }
  // ==================================================================

  return result;
}

uint64_t VideoEncoder::addCodecOption(const char* key, const char* val) {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex);

  if (enc->state != ENC_OPEN_CONTEXT && enc->state != ENC_OPEN_STREAMS) {
    llog(ERROR, "EncodingContext is in the wrong state. It should have been in %d or %d but it's in %d.", (int)ENC_OPEN_CONTEXT, (int)ENC_OPEN_STREAMS, (int)enc->state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (state != VE_FRESH) {
    llog(ERROR, "VideoEncoder is in the wrong state. It should have been in %d but it's in %d.", (int)VE_FRESH, (int)state);
    return MAKE_P_STAT(STREAM_BAD_STATE);
  }

  if (options.find(key) != options.end())
    // then the key is already set.
    return MAKE_P_STAT(OPTION_ALREADY_SET);
  options[key] = val;
  return 0;
}

uint64_t VideoEncoder::stop() {
  PILECV4J_TRACE;
  FakeMutextGuard g(enc->fake_mutex);

  // need to put it back or we get a double free when closing the overall context
  if (streams_original_set && video_avs) {
    if (isEnabled(TRACE))
      llog(TRACE, "Resetting video_avs(%" PRId64 ")->codecpar(%" PRId64 ")->extradata(%" PRId64 ") to %" PRId64,
          (uint64_t)video_avs, (uint64_t)(video_avs ? video_avs->codecpar : 0L),
          (uint64_t)((video_avs && video_avs->codecpar) ? video_avs->codecpar->extradata : 0L),
          (uint64_t)streams_original_set);
    video_avs->codecpar->extradata = streams_original_extradata;
    video_avs->codecpar->extradata_size = streams_original_extradata_size;
    streams_original_set = false;
  }

  state = VE_STOPPED;
  return 0;
}

VideoEncoder::~VideoEncoder() {
  PILECV4J_TRACE;
  stop(); // stop if not already stopped

  // non-recursive so can't cover stop()
  FakeMutextGuard g(enc->fake_mutex);

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
  uint64_t ret = (uint64_t)enc->ready();
  return ret;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_encodingContext_stop(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Stopping EncodingContext %" PRId64, nativeDef);
  EncodingContext* enc = (EncodingContext*)nativeDef;
  uint64_t ret = (uint64_t)enc->stop();
  return ret;
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_encode(uint64_t nativeDef, uint64_t matRef, int32_t isRgb) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Encoding mat at: %" PRId64 " as frame using video encoder at %" PRId64, matRef, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->encode(matRef, isRgb ? true : false);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_addCodecOption(uint64_t nativeDef, const char* key, const char* val) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "adding option for video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->addCodecOption(key,val);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setEncodingParameters(uint64_t nativeDef, int32_t pfps, int32_t pbufferSize, int64_t pminBitrate, int64_t pmaxBitrate) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting encoding parameters for video encoder at: %" PRId64 ": fps: %d, bufferSize: %d, min bitrate: %ld, max bitrate: %ld",
        nativeDef, (int)pfps, (int)pbufferSize, (long)pminBitrate, (long)pmaxBitrate);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setEncodingParameters(pfps, pbufferSize, pminBitrate, pmaxBitrate);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setFps(uint64_t nativeDef, int32_t pfps) {
  if (isEnabled(TRACE))
    llog(TRACE, "setting fps for video encoder at: %" PRId64 ": fps: %d",  nativeDef, (int)pfps);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setFps(pfps);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setBufferSize(uint64_t nativeDef, int32_t pbufferSize) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting buffer size for video encoder at: %" PRId64 ": bufferSize: %d",  nativeDef, (int)pbufferSize);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setBufferSize(pbufferSize);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setBitrate(uint64_t nativeDef, int64_t pminBitrate, int64_t pmaxBitrate) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting bitrate for video encoder at: %" PRId64 ": min bitrate: %ld, max bitrate: %ld",
        nativeDef, (long)pminBitrate, (long)pmaxBitrate);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setBitrate(pminBitrate, pmaxBitrate);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_setBitrate2(uint64_t nativeDef, int64_t pminBitrate) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "setting bitrate for video encoder at: %" PRId64 ": bitrate: %ld", nativeDef, (long)pminBitrate);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->setBitrate(pminBitrate);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable(uint64_t nativeDef, uint64_t matRef, int32_t isRgb) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable(matRef, isRgb ? true : false, -1, -1);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable2(uint64_t nativeDef, int32_t isRgb, int32_t width, int32_t height, int32_t stride) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable((bool)(isRgb ? true : false), width, height, stride, -1, -1);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable3(uint64_t nativeDef, int32_t isRgb, int32_t width, int32_t height) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable(isRgb ? true : false, width, height, -1, -1, -1);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable4(uint64_t nativeDef, uint64_t matRef, int32_t isRgb, int32_t dstW, int32_t dstH) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable(matRef, isRgb ? true : false, dstW, dstH);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable5(uint64_t nativeDef, int32_t isRgb, int32_t width, int32_t height, int32_t stride, int32_t dstW, int32_t dstH) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable((bool)(isRgb ? true : false), width, height, stride, dstW, dstH);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_enable6(uint64_t nativeDef, int32_t isRgb, int32_t width, int32_t height, int32_t dstW, int32_t dstH) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "enabling video encoder at: %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->enable(isRgb ? true : false, width, height, -1, dstW, dstH);
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
  return enc->stop();
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_videoEncoder_streaming(uint64_t nativeDef) {
  PILECV4J_TRACE;
  if (isEnabled(TRACE))
    llog(TRACE, "Stopping video encoder at %" PRId64, nativeDef);
  VideoEncoder* enc = (VideoEncoder*)nativeDef;
  return enc->streaming();
}

} /* extern "C" */
}

} /* namespace pilecv4j */

