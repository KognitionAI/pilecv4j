
#include <cstdint>
#include <utility>
#include <opencv2/core/mat.hpp>
#include <opencv2/highgui.hpp>

#include "common/jfloats.h"
#include "common/kog_exports.h"
#include "utils/log.h"

#define COMPONENT "CVMT"

using namespace pilecv4j::image;

namespace pilecv4j {
namespace image {

static std::string type2str(int type) {
  std::string r;

  uchar depth = type & CV_MAT_DEPTH_MASK;
  uchar chans = 1 + (type >> CV_CN_SHIFT);

  switch ( depth ) {
    case CV_8U:  r = "8U"; break;
    case CV_8S:  r = "8S"; break;
    case CV_16U: r = "16U"; break;
    case CV_16S: r = "16S"; break;
    case CV_32S: r = "32S"; break;
    case CV_32F: r = "32F"; break;
    case CV_64F: r = "64F"; break;
    case CV_16F: r = "16F"; break;
    default:     r = "User"; break;
  }

  r += "C";
  r += (chans+'0');

  return r;
}

extern "C" {
  KAI_EXPORT void* pilecv4j_image_CvRaster_getData(uint64_t native) {
    cv::Mat* mat = (cv::Mat*) native;

    if (!mat->isContinuous())
      return NULL;

    return mat->data;
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_copy(uint64_t native) {
    cv::Mat* mat = (cv::Mat*) native;

    if (!mat->isContinuous())
      return 0L;

    cv::Mat* newMat = new cv::Mat((*mat));
    return (uint64_t) newMat;
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_move(uint64_t native) {
    if (native == 0L)
      return 0L;
    return (uint64_t)(new cv::Mat(std::move(*((cv::Mat*) native))));
  }

  KAI_EXPORT void pilecv4j_image_CvRaster_freeByMove(uint64_t native) {
    if (native != 0L) {
      // explicit call to the move constructor moving the
      // resources into m, and then closing m at the end of the
      // block. The allows the mat passed as a parameter to
      // carry on with its lifecycle with all of its resources
      // freed.
      cv::Mat m(std::move(*((cv::Mat*) native)));
    }
  }

  KAI_EXPORT void pilecv4j_image_CvRaster_assign(uint64_t destHandle, uint64_t srcHandle) {
    cv::Mat* dst = (cv::Mat*) destHandle;
    cv::Mat* src = (cv::Mat*) srcHandle;

    *dst = *src;
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_makeMatFromRawDataReference(uint32_t rows, uint32_t cols, uint32_t type, uint64_t dataLong) {
    void* data = (void*) dataLong;
    cv::Mat* newMat = new cv::Mat(rows, cols, type, data);

    return (uint64_t) newMat;
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_makeMdMatFromRawDataReference(int32_t ndims, int32_t* sizes, uint32_t type, uint64_t dataLong) {
    if (isEnabled(TRACE))
      log(TRACE, COMPONENT, "Making %d dimensional Mat of type %s", (int)ndims, type2str(type).c_str());
    void* data = (void*) dataLong;
    int* dims = new int[ndims];
    for (int i = 0; i < ndims; i++)
      dims[i] = (int)sizes[i];
    cv::Mat* newMat = new cv::Mat((int)ndims, dims, type, data);
    delete[] dims;
    return (uint64_t) newMat;
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_defaultMat() {
    cv::Mat* ret = new cv::Mat();
    return (uint64_t) ret;
  }

  KAI_EXPORT void pilecv4j_image_CvRaster_showImage(const char* name, uint64_t native) {
    cv::Mat* mat = (cv::Mat*) native;
    cv::String sname(name);
    cv::namedWindow(sname,cv::WINDOW_NORMAL);
    if (mat)
      cv::imshow(sname,*mat);
  }

  KAI_EXPORT void pilecv4j_image_CvRaster_updateWindow(const char* name, uint64_t native) {
    cv::Mat* mat = (cv::Mat*) native;
    cv::String sname(name);
    cv::imshow(sname,*mat);
  }

  KAI_EXPORT int32_t pilecv4j_image_CvRaster_fetchEvent(int32_t millisToSleep) {
    int key = cv::waitKey(millisToSleep);
    return key;
  }

  KAI_EXPORT void pilecv4j_image_CvRaster_destroyWindow(const char* name) {
    cv::String sname(name);
    cv::destroyWindow(sname);
  }

  KAI_EXPORT bool pilecv4j_image_CvRaster_isWindowClosed(const char* name) {
    cv::String sname(name);
    return cv::getWindowProperty(sname, 0) < 0;
  }
}
}
}
