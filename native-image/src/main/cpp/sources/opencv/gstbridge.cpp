#include <opencv2/core/mat.hpp>
#include "imagemaker.h"

#include <memory>

class ImageMaker : public ai::kognition::pilecv4j::ImageMaker {
public:
  virtual uint64_t makeImage(int height, int width, int stride, void* data){
    cv::Mat* cvmat = new cv::Mat(height, width, CV_8UC3, data, stride);
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
};

static ai::kognition::pilecv4j::ImageMaker* imaker = new ImageMaker();

extern "C" {

  uint64_t get_im_maker() {
    return (uint64_t)imaker;
  }

}
