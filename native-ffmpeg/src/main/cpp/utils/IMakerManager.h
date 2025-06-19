/*
 * IMakerManager.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _IMAKERMANAGER_H_
#define _IMAKERMANAGER_H_

#include <libavcodec/avcodec.h>
extern "C" {
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
}
#include <stdint.h>
#include "common/imagemaker.h"

namespace pilecv4j
{
namespace ffmpeg
{

class IMakerManager
{
public:

  struct Transform {
    // ========================================
    // Source (domain) of the xform
    int srcW = -1;
    int srcH = -1;
    size_t srcS = 0;
    ai::kognition::pilecv4j::PixelFormat srcPcv4jFmt = ai::kognition::pilecv4j::UNKNOWN;
    AVPixelFormat srcAvFmt = AV_PIX_FMT_NONE;
    // ========================================

    // ========================================
    // Destination (range) of the xform
    int dstW = -1;
    int dstH = -1;
    AVPixelFormat dstAvFmt = AV_PIX_FMT_NONE;
    // ========================================

    SwsContext *conversion = nullptr;
    bool supportsCurFormat = false;

    ~Transform() {
      if (conversion)
        sws_freeContext(conversion);
    }
  };

  static ai::kognition::pilecv4j::ImageMaker* getIMaker();

  static void setIMaker(ai::kognition::pilecv4j::ImageMaker* imaker);

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
  static uint64_t createMatFromFrame(AVFrame *pFrame, int dstMaxDim, SwsContext** colorCvrt, int32_t& isRgb,
      AVPixelFormat& lastFormatUsed, int& dstWo, int& dstHo, AVPixelFormat pixFmt);
  static void freeImage(uint64_t mat);

  static uint64_t setupTransform(uint64_t mat, bool isRgb, struct AVCodecContext* encoder, Transform* xform);

  static uint64_t setupTransform(int srcWidth, int srcHeight, int srcStride, ai::kognition::pilecv4j::PixelFormat srcPixfmt, AVCodecContext* avcc, int dstW, int dstH, Transform* xform);

  static uint64_t createFrameFromMat(const Transform* xform, uint64_t mat, bool isRgb, AVCodecContext* encoder, AVFrame** frame);

  static AVPixelFormat convert(ai::kognition::pilecv4j::PixelFormat pxfmt);

  static void freeFrame(AVFrame** frame);
};

}
} /* namespace pilecv4j */

#endif /* _IMAKERMANAGER_H_ */
