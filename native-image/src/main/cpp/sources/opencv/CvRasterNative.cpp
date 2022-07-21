
#include <cstdint>
#include <utility>
#include <opencv2/core/mat.hpp>
#include <opencv2/highgui.hpp>

#include "jfloats.h"
#include "kog_exports.h"

extern "C" {
KAI_EXPORT void* pilecv4j_image_CvRaster_getData(uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;

	if (!mat->isContinuous())
		return NULL;

	return mat->ptr(0);
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

void pilecv4j_image_CvRaster_assign(uint64_t destHandle, uint64_t srcHandle) {
	cv::Mat* dst = (cv::Mat*) destHandle;
	cv::Mat* src = (cv::Mat*) srcHandle;

	*dst = *src;
}


uint64_t pilecv4j_image_CvRaster_makeMatFromRawDataReference(uint32_t rows, uint32_t cols, uint32_t type, uint64_t dataLong) {
	void* data = (void*) dataLong;
	cv::Mat* newMat = new cv::Mat(rows, cols, type, data);

	return (uint64_t) newMat;
}

uint64_t pilecv4j_image_CvRaster_defaultMat() {
	cv::Mat* ret = new cv::Mat();
	return (uint64_t) ret;
}

void pilecv4j_image_CvRaster_showImage(const char* name, uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;
	cv::String sname(name);
	cv::namedWindow(sname,cv::WINDOW_NORMAL);
	if (mat) {
		cv::imshow(sname,*mat);
	}
}

void pilecv4j_image_CvRaster_updateWindow(const char* name, uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;
	cv::String sname(name);
	cv::imshow(sname,*mat);
}

int32_t pilecv4j_image_CvRaster_fetchEvent(int32_t millisToSleep) {
	int key = cv::waitKey(millisToSleep);
	return key;
}

void pilecv4j_image_CvRaster_destroyWindow(const char* name) {
	cv::String sname(name);
	cv::destroyWindow(sname);
}

bool pilecv4j_image_CvRaster_isWindowClosed(const char* name) {
	cv::String sname(name);
	return cv::getWindowProperty(sname, 0) < 0;
}
}

