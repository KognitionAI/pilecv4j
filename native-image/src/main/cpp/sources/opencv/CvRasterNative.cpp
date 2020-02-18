
#include <cstdint>
#include <utility>
#include <opencv2/core/mat.hpp>
#include <opencv2/highgui.hpp>

#include "jfloats.h"
#include "kog_exports.h"

extern "C" {
  KAI_EXPORT void* CvRaster_getData(uint64_t native);
  KAI_EXPORT uint64_t CvRaster_copy(uint64_t native);
  KAI_EXPORT uint64_t CvRaster_move(uint64_t native);
  KAI_EXPORT void CvRaster_assign(uint64_t destHandle, uint64_t srcHandle);
  KAI_EXPORT uint64_t CvRaster_makeMatFromRawDataReference(uint32_t rows, uint32_t cols, uint32_t type, uint64_t dataLong);
  KAI_EXPORT uint64_t CvRaster_defaultMat();
  KAI_EXPORT void CvRaster_showImage(const char* name, uint64_t native);
  KAI_EXPORT void CvRaster_updateWindow(const char* name, uint64_t native);
  KAI_EXPORT int32_t CvRaster_fetchEvent(int32_t millisToSleep);
  KAI_EXPORT void CvRaster_destroyWindow(const char* name);
  KAI_EXPORT bool CvRaster_isWindowClosed(const char* name);
}

void* CvRaster_getData(uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;

	if (!mat->isContinuous())
		return NULL;

	return mat->ptr(0);
}

uint64_t CvRaster_copy(uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;

	if (!mat->isContinuous())
		return 0L;

	cv::Mat* newMat = new cv::Mat((*mat));
	return (uint64_t) newMat;
}

uint64_t CvRaster_move(uint64_t native) {
  if (native == 0L)
    return 0L;
  return (uint64_t)(new cv::Mat(std::move(*((cv::Mat*) native))));
}

void CvRaster_assign(uint64_t destHandle, uint64_t srcHandle) {
	cv::Mat* dst = (cv::Mat*) destHandle;
	cv::Mat* src = (cv::Mat*) srcHandle;

	*dst = *src;
}


uint64_t CvRaster_makeMatFromRawDataReference(uint32_t rows, uint32_t cols, uint32_t type, uint64_t dataLong) {
	void* data = (void*) dataLong;
	cv::Mat* newMat = new cv::Mat(rows, cols, type, data);

	return (uint64_t) newMat;
}

uint64_t CvRaster_defaultMat() {
	cv::Mat* ret = new cv::Mat();
	return (uint64_t) ret;
}

void CvRaster_showImage(const char* name, uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;
	cv::String sname(name);
	cv::namedWindow(sname,cv::WINDOW_NORMAL);
	if (mat) {
		cv::imshow(sname,*mat);
	}
}

void CvRaster_updateWindow(const char* name, uint64_t native) {
	cv::Mat* mat = (cv::Mat*) native;
	cv::String sname(name);
	cv::imshow(sname,*mat);
}

int32_t CvRaster_fetchEvent(int32_t millisToSleep) {
	int key = cv::waitKey(millisToSleep);
	return key;
}

void CvRaster_destroyWindow(const char* name) {
	cv::String sname(name);
	cv::destroyWindow(sname);
}

bool CvRaster_isWindowClosed(const char* name) {
	cv::String sname(name);
	return cv::getWindowProperty(sname, 0) < 0;
}
