
#include <cstdint>
#include <opencv2/core/mat.hpp>
#include <opencv2/highgui.hpp>

#include "jfloats.h"

extern "C" {
void* CvRaster_getData(uint64_t native);
uint64_t CvRaster_copy(uint64_t native);
void CvRaster_assign(uint64_t destHandle, uint64_t srcHandle);
uint64_t CvRaster_makeMatFromRawDataReference(uint32_t rows, uint32_t cols, uint32_t type, uint64_t dataLong);
uint64_t CvRaster_defaultMat();
void CvRaster_showImage(const char* name, uint64_t native);
void CvRaster_updateWindow(const char* name, uint64_t native);
int32_t CvRaster_fetchEvent(int32_t millisToSleep);
void CvRaster_destroyWindow(const char* name);
void CvRaster_noArray();
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
