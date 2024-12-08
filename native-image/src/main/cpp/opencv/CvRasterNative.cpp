
#include <cstdint>
#include <utility>
#include <opencv2/core/mat.hpp>
#include <opencv2/highgui.hpp>

#include "common/jfloats.h"
#include "common/kog_exports.h"
#include "utils/log.h"

#define COMPONENT "CVMT"
#define MAX_MAT_DIMMS 30

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

    if (mat->dims == 0) { // if there's no data, just return an empty mat
      cv::Mat* newMat = new cv::Mat();
      return (uint64_t)newMat;
    }

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

  KAI_EXPORT void pilecv4j_image_CvRaster_zeroCopyDecode(uint64_t srcHandle, int32_t flags, uint64_t destHandle) {
    cv::Mat* dst = (cv::Mat*) destHandle;
    cv::Mat* src = (cv::Mat*) srcHandle;

    cv::imdecode(*src, flags, dst);
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_makeMatFromRawDataReference(uint32_t rows, uint32_t cols, uint32_t type, uint64_t dataLong) {
    void* data = (void*) dataLong;
    cv::Mat* newMat = new cv::Mat(rows, cols, type, data);

    return (uint64_t) newMat;
  }

  KAI_EXPORT uint64_t pilecv4j_image_CvRaster_makeMdMatFromRawDataReference(int32_t ndims, int32_t* sizes, uint32_t type, uint64_t dataLong) {
    if (isEnabled(TRACE))
      log(TRACE, COMPONENT, "Making %d dimensional Mat of type %s", (int)ndims, type2str(type).c_str());
    if (ndims > MAX_MAT_DIMMS) {
      log(ERROR, COMPONENT, "Cannot create a mat with more dimensions than %d. Requested %d", (int)MAX_MAT_DIMMS, (int)ndims);
      return 0L;
    }
    void* data = (void*) dataLong;
    int dims[MAX_MAT_DIMMS];
    for (int i = 0; i < ndims; i++)
      dims[i] = (int)sizes[i];
    cv::Mat* newMat = new cv::Mat((int)ndims, dims, type, data);
    return (uint64_t) newMat;
  }

  KAI_EXPORT void pilecv4j_image_CvRaster_inplaceReshape(uint64_t native, int32_t cn, int32_t ndims, int32_t* sizes) {
    if (isEnabled(TRACE))
      log(TRACE, COMPONENT, "Reshaping to a %d dimensional Mat with %d channels", (int)ndims, (int)cn);
    if (!native) {
      log(ERROR, COMPONENT, "NULL mat passed to inplaceReshape.");
      return;
    }
    if (ndims > MAX_MAT_DIMMS) {
      log(ERROR, COMPONENT, "Cannot reshape a mat with more dimensions than %d. Requested %d", (int)MAX_MAT_DIMMS, (int)ndims);
      return;
    }
    cv::Mat* mat = (cv::Mat*)native;
    int dims[MAX_MAT_DIMMS];
    for (int i = 0; i < ndims; i++)
      dims[i] = (int)sizes[i];
    (*mat) = mat->reshape(cn, ndims, dims);
  }

  // This should only be called on a mat that has no control over its data and was itself constructed with a data
  //   pointer, OR where another Mat is still controlling the data. The java side checks for this.
  KAI_EXPORT int32_t pilecv4j_image_CvRaster_inplaceRemake(uint64_t native, int32_t ndims, int32_t* sizes, int32_t type, uint64_t maxSize) {
    if (isEnabled(TRACE))
      log(TRACE, COMPONENT, "Remaking to a %d dimensional Mat with type %s", (int)ndims, type2str(type).c_str());
    if (!native) {
      log(ERROR, COMPONENT, "NULL mat passed to inplaceRemake.");
      return 0;
    }
    if (ndims > MAX_MAT_DIMMS) {
      log(ERROR, COMPONENT, "Cannot remake a mat with more dimensions than %d. Requested %d", (int)MAX_MAT_DIMMS, (int)ndims);
      return 0;
    }
    int dims[MAX_MAT_DIMMS];
    std::size_t numBytes = CV_ELEM_SIZE(type);
    for (int i = 0; i < ndims; i++) {
      dims[i] = (int)sizes[i];
      numBytes *= dims[i];
    }
    cv::Mat* mat = (cv::Mat*)native;
    if (numBytes > maxSize) {
      log(ERROR, COMPONENT, "Cannot remake a mat to a larger size than the original");
      return 0;
    }

    uint8_t* data = (uint8_t*)mat->data;
    cv::Mat remade(ndims, dims, type, data);
    (*mat) = remade;
    return 1;
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

