/*
 * IMakerManager.h
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#ifndef _IMAKERMANAGER_H_
#define _IMAKERMANAGER_H_

extern "C" {
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
}
#include <stdint.h>
#include "imagemaker.h"

namespace pilecv4j
{

class IMakerManager
{
public:

  struct Transform {
    int w = -1;
    int h = -1;
    size_t stride = 0;
    ai::kognition::pilecv4j::PixelFormat origFmt = ai::kognition::pilecv4j::UNKNOWN;
    AVPixelFormat srcFmt = AV_PIX_FMT_NONE;
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
  static uint64_t createMatFromFrame(AVFrame *pFrame, SwsContext** colorCvrt, int32_t& isRgb, AVPixelFormat& lastFormatUsed);

  static void freeImage(uint64_t mat);

  static uint64_t setupTransform(uint64_t mat, bool isRgb, AVCodecContext* encoder, Transform* xform);

  static uint64_t setupTransform(int width, int height, size_t stride, ai::kognition::pilecv4j::PixelFormat pixfmt, AVCodecContext* encoder, Transform* xform);

  static uint64_t createFrameFromMat(const Transform* xform, uint64_t mat, bool isRgb, AVCodecContext* encoder, AVFrame** frame);

  static AVPixelFormat convert(ai::kognition::pilecv4j::PixelFormat pxfmt);

  static void freeFrame(AVFrame** frame);
};

} /* namespace pilecv4j */

#endif /* _IMAKERMANAGER_H_ */
