/*
 * IMakerManager.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "common/kog_exports.h"

#include "utils/log.h"
#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/IMakerManager.h"

#include "utils/timing.h"

extern "C" {
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

#include <stdlib.h>

//========================================================================
// This is the bridge to lib-image that circumvents a compile dependency
static ai::kognition::pilecv4j::ImageMaker* imaker;
//========================================================================

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "IMMA"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

TIME_DECL(alloc_mat);
TIME_DECL(cvt_color);
TIME_DECL(create_color_cvt);

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

ai::kognition::pilecv4j::ImageMaker* IMakerManager::getIMaker() {
  return imaker;
}

void IMakerManager::setIMaker(ai::kognition::pilecv4j::ImageMaker* pimaker) {
  imaker = pimaker;
}

static AVPixelFormat upgradePixFormatIfNecessary(AVPixelFormat cur) {
  AVPixelFormat pixFormat;
  bool upgraded = true;
  switch (cur) {
  case AV_PIX_FMT_YUVJ420P :
      pixFormat = AV_PIX_FMT_YUV420P;
      break;
  case AV_PIX_FMT_YUVJ422P  :
      pixFormat = AV_PIX_FMT_YUV422P;
      break;
  case AV_PIX_FMT_YUVJ444P   :
      pixFormat = AV_PIX_FMT_YUV444P;
      break;
  case AV_PIX_FMT_YUVJ440P :
      pixFormat = AV_PIX_FMT_YUV440P;
      break;
  default:
      upgraded = false;
      pixFormat = cur;
      break;
  }
  if (upgraded)
    llog(TRACE, "Upgrading pixel format from %d to %d", cur, pixFormat);
  return pixFormat;
}

template <typename T>
static inline T align128(T x) {
  return (x & (T)127) ? (((x >> 7) + 1) << 7) : x;
}

/**
 * This will take the current frame and create an opencv Mat. It will set isRgb based
 * on whether or not the mat being returned is rgb or bgr. It will do the least amount
 * of conversion possible but it prefers pixFmt which should be either AV_PIX_FMT_RGB24
 * or AV_PIX_FMT_BGR24. If it's anything else the results are undefined.
 * This means it will only return a non pixFmt mat if the source frame is already in
 * the other format.
 *
 * The SwsContext** passed will be used if the format hasn't changed and it's not null.
 * Otherwise it will be created and returned through the parameter requiring the caller
 * to eventually free it with sws_freeContext. If it's not null but the format changed
 * then the method will free the existing one before creating the new one.
 */
uint64_t IMakerManager::createMatFromFrame(AVFrame *pFrame, SwsContext** colorCvrt, int32_t& isRgb,
    AVPixelFormat& lastFormatUsed, AVPixelFormat pixFmt) {
  PILECV4J_TRACE;
  if (imaker == nullptr)
    return MAKE_P_STAT(NO_IMAGE_MAKER_SET);

  const int32_t w = pFrame->width;
  const int32_t h = pFrame->height;

  uint64_t mat;
  AVPixelFormat curFormat = (AVPixelFormat)pFrame->format;
  curFormat = upgradePixFormatIfNecessary(curFormat);
  if (curFormat != AV_PIX_FMT_RGB24 && curFormat != AV_PIX_FMT_BGR24) {
    TIME_OPEN(create_color_cvt);
    // use the existing setup if it's there already.
    SwsContext* swCtx = *colorCvrt;
    if (swCtx == nullptr || lastFormatUsed != curFormat) {
      lastFormatUsed = curFormat;
      if (swCtx)
        sws_freeContext(swCtx);

      *colorCvrt = swCtx =
          sws_getContext(
              w,
              h,
              curFormat,
              w,
              h,
              pixFmt,
              SWS_POINT,NULL,NULL,NULL
          );
      TIME_CAP(create_color_cvt);
    }

    int32_t stride = 3 * w;
    ai::kognition::pilecv4j::MatAndData matPlus = imaker->allocateImage(h,w);
    mat = matPlus.mat;
    uint8_t* matData = (uint8_t*)matPlus.data;
    uint8_t *rgb24[1] = { matData };
    int rgb24_stride[1] = { stride };
    TIME_OPEN(cvt_color);
    sws_scale(swCtx,pFrame->data, pFrame->linesize, 0, h, rgb24, rgb24_stride);
    TIME_CAP(cvt_color);
    isRgb = pixFmt == AV_PIX_FMT_RGB24 ? 1 : 0;
  } else {
    TIME_OPEN(alloc_mat);
    mat = imaker->allocateImageWithCopyOfData(h,w,w * 3,pFrame->data[0]);
    TIME_CAP(alloc_mat);
    isRgb = (curFormat == AV_PIX_FMT_RGB24) ? 1 : 0;
  }

  return mat;
}

void IMakerManager::freeImage(uint64_t mat) {
  imaker->freeImage(mat);
}

AVPixelFormat IMakerManager::convert(ai::kognition::pilecv4j::PixelFormat pfmt) {
  switch (pfmt) {
  case ai::kognition::pilecv4j::RGB24:
    return AV_PIX_FMT_RGB24;
  case ai::kognition::pilecv4j::BGR24:
    return AV_PIX_FMT_BGR24;
  default:
    return AV_PIX_FMT_NONE;
  }
}

uint64_t IMakerManager::setupTransform(uint64_t mat, bool isRgb, AVCodecContext* encoder, Transform* xform) {
  ai::kognition::pilecv4j::RawRaster raster;
  if (!imaker->extractImageDetails(mat, isRgb, &raster)) {
    llog(ERROR, "Failed to extract image details from the opencv mat");
    return MAKE_P_STAT(FAILED_CREATE_FRAME);
  }

  return setupTransform(raster.w, raster.h, raster.stride, raster.pixFormat, encoder, raster.w, raster.h, xform);
}

uint64_t IMakerManager::setupTransform(int srcWidth, int srcHeight, size_t srcStride, ai::kognition::pilecv4j::PixelFormat srcPixfmt, AVCodecContext* avcc, int dstW, int dstH, Transform* xform) {
  if (avcc->codec_type != AVMEDIA_TYPE_VIDEO) {
    llog(ERROR, "Cannot create an Transform from an opencv Mat for an encoder that isn't a video encoder.");
    return MAKE_P_STAT(UNSUPPORTED_CODEC);
  }

  xform->srcW = srcWidth;
  xform->srcH = srcHeight;
  srcStride = srcStride < 0 ? (srcWidth * 3) : srcStride;
  xform->srcS = srcStride;
  xform->srcPcv4jFmt = srcPixfmt;
  xform->srcAvFmt = convert(srcPixfmt);

  // figure out if I need to do a conversion.
  xform->supportsCurFormat = xform->srcAvFmt == avcc->pix_fmt;
  llog(TRACE, "Supports current format %s. Covert from %d to %d", xform->supportsCurFormat ? "true" : "false",(int) xform->srcAvFmt, (int) avcc->pix_fmt );

  xform->dstW = dstW;
  xform->dstH = dstH;
  xform->dstAvFmt = avcc->pix_fmt;

  {
    //llog(TRACE, "STEP 9: sws_getContext: width %d, height %d, AV_PIX_FMT_RGB24 (2:%d), pix_fmt (0:%d)", (int) xform->w, (int)xform->h, (int) xform->srcFmt, (int)avcc->pix_fmt);

    xform->conversion = sws_getContext(
        xform->srcW, xform->srcH, xform->srcAvFmt,
        xform->dstW, xform->dstH, xform->dstAvFmt,
        SWS_BILINEAR,
        NULL, NULL, NULL);

    if (!xform->conversion) {
      llog(ERROR, "Couldn't create pixel format conversion");
      return MAKE_P_STAT(FAILED_CREATE_FRAME);
    }
  }

  return 0;
}

/**
 * If the ppframe returned is not null, then freeing the frame should use freeFrame
 * on this class.
 */
uint64_t IMakerManager::createFrameFromMat(const Transform* xform, uint64_t mat, bool isRgb, AVCodecContext* encoder, AVFrame** ppframe) {
  ai::kognition::pilecv4j::RawRaster raster;
  if (!imaker->extractImageDetails(mat, isRgb, &raster)) {
    llog(ERROR, "Failed to extract image details from the opencv mat");
    return MAKE_P_STAT(FAILED_CREATE_FRAME);
  }

  // check that the image hasn't changed.
  if (raster.h != xform->srcH || raster.w != xform->srcW || raster.pixFormat != xform->srcPcv4jFmt || raster.stride != (int)xform->srcS) {
    llog (ERROR, "A critical dimension of the input images seems to have changed.");
    return MAKE_P_STAT(STREAM_CHANGED);
  }

  AVFrame* frame = *ppframe;
  if (!frame) {
    const int dstW = xform->dstW;
    const int dstH = xform->dstH;
    *ppframe = frame = av_frame_alloc();
    av_image_alloc(frame->data, frame->linesize, dstW, dstH, encoder->pix_fmt, 1);
    frame->width = dstW;
    frame->height = dstH;
    frame->format = static_cast<int>(encoder->pix_fmt);
  }

  SwsContext *conversion = xform->conversion;
  const int stride[] = {static_cast<int>(raster.stride)};
  sws_scale(conversion, (const uint8_t* const*)(&(raster.data)), stride, 0, xform->srcH, frame->data, frame->linesize);

  return 0;
}

void IMakerManager::freeFrame(AVFrame** ppframe) {
  if (ppframe && *ppframe) {
    av_freep(&(*ppframe)->data);
    av_frame_free(ppframe);
    *ppframe= nullptr;
  }
}

void displayImageMakerTimings() {
  TIME_DISPLAY("create color conversion", create_color_cvt);
  TIME_DISPLAY("allocate mat for image", alloc_mat);
  TIME_DISPLAY("conver to RGB", cvt_color);
}

extern "C" {

// exposed to java
KAI_EXPORT void pcv4j_ffmpeg2_imageMaker_set(uint64_t im) {
  imaker = (ai::kognition::pilecv4j::ImageMaker*)im;
}

}

}
} /* namespace pilecv4j */

