/*
 * IMakerManager.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "imagemaker.h"
#include "kog_exports.h"

#include "utils/log.h"
#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/IMakerManager.h"

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
#define COMPONENT "IMMA"

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


/**
 * This will take the current frame and create an opencv Mat. It will set isRgb based
 * on whether or not the mat being returned is rgb or bgr. It will do the least amount
 * of conversion possible but it prefers rgb. This means it will only return a bgr mat
 * if the source frame is already in bgr.
 *
 * The SwsContext** passed with be used if the format hasn't changed and it's not null.
 * Otherwise it will be created and returned through the parameter requiring the caller
 * to eventually free it with sws_freeContext. If it's not null but the format changed
 * then the method will free the existing one before creating the new one.
 */
uint64_t IMakerManager::createMatFromFrame(AVFrame *pFrame, SwsContext** colorCvrt, int32_t& isRgb, AVPixelFormat& lastFormatUsed) {
  if (imaker == nullptr)
    return MAKE_P_STAT(NO_IMAGE_MAKER_SET);

  const int32_t w = pFrame->width;
  const int32_t h = pFrame->height;

  uint64_t mat;
  AVPixelFormat curFormat = (AVPixelFormat)pFrame->format;
  curFormat = upgradePixFormatIfNecessary(curFormat);
  if (curFormat != AV_PIX_FMT_RGB24 && curFormat != AV_PIX_FMT_BGR24) {
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
              AV_PIX_FMT_RGB24,
              SWS_BILINEAR,NULL,NULL,NULL
          );
    }

    int32_t stride = 3 * w;
    ai::kognition::pilecv4j::MatAndData matPlus = imaker->allocateImage(h,w);
    mat = matPlus.mat;
    uint8_t* matData = (uint8_t*)matPlus.data;
    uint8_t *rgb24[1] = { matData };
    int rgb24_stride[1] = { stride };
    sws_scale(swCtx,pFrame->data, pFrame->linesize, 0, h, rgb24, rgb24_stride);
    isRgb = 1;
  } else {
    mat = imaker->allocateImageWithCopyOfData(h,w,w * 3,pFrame->data[0]);
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

  return setupTransform(raster.w, raster.h, raster.stride, raster.pixFormat, encoder, xform);
}

uint64_t IMakerManager::setupTransform(int width, int height, size_t stride, ai::kognition::pilecv4j::PixelFormat pixfmt, AVCodecContext* avcc, Transform* xform) {
  if (avcc->codec_type != AVMEDIA_TYPE_VIDEO) {
    llog(ERROR, "Cannot create an Transform from an opencv Mat for an encoder that isn't a video encoder.");
    return MAKE_P_STAT(UNSUPPORTED_CODEC);
  }

  xform->w = width;
  xform->h = height;
  stride = stride < 0 ? (width * 3) : stride;
  xform->stride = stride;
  xform->origFmt = pixfmt;
  xform->srcFmt = convert(pixfmt);

  // figure out if I need to do a conversion.
  xform->supportsCurFormat = xform->srcFmt == avcc->pix_fmt;

  llog(TRACE, "Supports current format %s. Covert from %d to %d", xform->supportsCurFormat ? "true" : "false",(int) xform->srcFmt, (int) avcc->pix_fmt );

  {
    //llog(TRACE, "STEP 9: sws_getContext: width %d, height %d, AV_PIX_FMT_RGB24 (2:%d), pix_fmt (0:%d)", (int) xform->w, (int)xform->h, (int) xform->srcFmt, (int)avcc->pix_fmt);

    xform->conversion = sws_getContext(xform->w, xform->h,
        xform->srcFmt,
        xform->w, xform->h,
        avcc->pix_fmt,
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
  if (raster.h != xform->h || raster.w != xform->w || raster.pixFormat != xform->origFmt || raster.stride != (int)xform->stride) {
    llog (ERROR, "A critical dimension of the input images seems to have changed.");
    return MAKE_P_STAT(STREAM_CHANGED);
  }

  int width = xform->w;
  int height = xform->h;
  AVFrame* frame = *ppframe;
  if (!frame) {
    *ppframe = frame = av_frame_alloc();
    av_image_alloc(frame->data, frame->linesize, width, height, encoder->pix_fmt, 1);
    frame->width = width;
    frame->height = height;
    frame->format = static_cast<int>(encoder->pix_fmt);
  }

  SwsContext *conversion = xform->conversion;
  const int stride[] = {static_cast<int>(raster.stride)};
  sws_scale(conversion, (const uint8_t* const*)(&(raster.data)), stride, 0, height, frame->data, frame->linesize);

  return 0;
}

void IMakerManager::freeFrame(AVFrame** ppframe) {
  if (ppframe && *ppframe) {
    av_freep(&(*ppframe)->data);
    av_frame_free(ppframe);
    *ppframe= nullptr;
  }
}

} /* namespace pilecv4j */

extern "C" {

// exposed to java
KAI_EXPORT void pcv4j_ffmpeg2_imageMaker_set(uint64_t im) {
  imaker = (ai::kognition::pilecv4j::ImageMaker*)im;
}

}

