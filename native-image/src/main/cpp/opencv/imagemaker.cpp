#include <opencv2/core/mat.hpp>
#include "common/imagemaker.h"
#include "common/kog_exports.h"

#include <memory>

using namespace ai::kognition::pilecv4j;

static PixelFormat mapCvType(int cvType, bool isRgb) {

  switch (cvType) {
  case CV_8UC3:
    return isRgb ? RGB24 : BGR24;
  default:
    return UNKNOWN;
  }
}

class ImageMakerImpl : public ImageMaker {
public:
  virtual uint64_t makeImage(int height, int width, int stride, void* data){
    cv::Mat* cvmat = new cv::Mat(height, width, CV_8UC3, data, stride);
    return (uint64_t)cvmat;
  }

  virtual MatAndData allocateImage(int height, int width){
    // Over-allocate one slack row: this buffer is used as the destination for
    // libswscale's sws_scale(), whose SIMD output writers can store past the
    // end of the last row when (width * 3) isn't a multiple of the SIMD write
    // size (e.g. any 1080-wide portrait stream: 1080*3 = 3240, not a multiple
    // of 32). Without the slack this corrupts the heap and eventually aborts
    // with "free(): invalid next size". Landscape 1920-wide frames only worked
    // by luck (1920*3 = 5760 = 32*180).
    // The returned Mat is a rowRange view of the padded backing buffer; the
    // view keeps the refcounted backing alive, reports the true height, and
    // remains continuous.
    cv::Mat backing(height + 1, width, CV_8UC3);
    cv::Mat* cvmat = new cv::Mat(backing.rowRange(0, height));
    return {(uint64_t)cvmat, cvmat->data};
  }

  virtual uint64_t allocateImageWithCopyOfData(int height, int width, int stride, void* data) {
    cv::Mat* cvmat = new cv::Mat();
    cv::Mat dataWrapper(height, width, CV_8UC3, data, stride);
    dataWrapper.copyTo(*cvmat);
    return (uint64_t)cvmat;
  }

  virtual uint64_t allocateImageWithData(int height, int width, int stride, void* data) {
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

  virtual bool extractImageDetails(uint64_t matRef, bool isRgb, RawRaster* rasterToFill) {
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

static ImageMaker* imaker = new ImageMakerImpl();

extern "C" {

  KAI_EXPORT uint64_t pilecv4j_image_get_im_maker() {
    return (uint64_t)imaker;
  }

}
