#include <opencv2/core/mat.hpp>
#include "imagemaker.h"
#include "kog_exports.h"

#include <memory>

static ai::kognition::pilecv4j::PixelFormat mapCvType(int cvType, bool isRgb) {

  switch (cvType) {
  case CV_8UC3:
    return isRgb ? ai::kognition::pilecv4j::RGB24 : ai::kognition::pilecv4j::BGR24;
  default:
    return ai::kognition::pilecv4j::UNKNOWN;
  }
}

class ImageMaker : public ai::kognition::pilecv4j::ImageMaker {
public:
  virtual uint64_t makeImage(int height, int width, int stride, void* data){
    cv::Mat* cvmat = new cv::Mat(height, width, CV_8UC3, data, stride);
    return (uint64_t)cvmat;
  }

  virtual ai::kognition::pilecv4j::MatAndData allocateImage(int height, int width){
    cv::Mat* cvmat = new cv::Mat(height, width, CV_8UC3);
    return {(uint64_t)cvmat, cvmat->data};
  }

  virtual uint64_t allocateImageWithCopyOfData(int height, int width, int stride, void* data) {
    cv::Mat* cvmat = new cv::Mat();
    cv::Mat dataWrapper(height, width, CV_8UC3, data, stride);
    dataWrapper.copyTo(*cvmat);
    return (uint64_t)cvmat;
  }

  virtual void freeImage(uint64_t mat) {
    delete ((cv::Mat*)mat);
  }

  virtual uint64_t copy(uint64_t mat) {
    cv::Mat* cvmat = (cv::Mat*)mat;
    cv::Mat* ret = new cv::Mat();
    uint64_t rret = (uint64_t)ret;
    cvmat->copyTo(*ret);
    return rret;
  }

  virtual bool extractImageDetails(uint64_t matRef, bool isRgb, ai::kognition::pilecv4j::RawRaster* rasterToFill) {
    if (matRef == 0)
      return false;
    if (!rasterToFill)
      return false;

    cv::Mat* cvmat = (cv::Mat*)matRef;

    if (!cvmat->isContinuous())
      return false;

    rasterToFill->pixFormat = mapCvType(cvmat->type(), isRgb);
    rasterToFill->data = cvmat->data;
    rasterToFill->w = cvmat->cols;
    rasterToFill->h = cvmat->rows;
    rasterToFill->stride = cvmat->step;

    return true;
  }
};

static ai::kognition::pilecv4j::ImageMaker* imaker = new ImageMaker();

extern "C" {

  KAI_EXPORT uint64_t get_im_maker() {
    return (uint64_t)imaker;
  }

}
