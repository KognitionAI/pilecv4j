#include "com_jiminger_image_CvRasterNative.h"

#include <cstdint>
#include <opencv2/core/mat.hpp>
#include <opencv2/highgui.hpp>

static void throwIllegalStateException( JNIEnv *env, const char* message ) {
  static const char* iseClassName = "java/lang/IllegalStateException";

  jclass exClass = env->FindClass(iseClassName);
  if (exClass == NULL)
    return; // nothing I can do, really.

  env->ThrowNew(exClass, message );
}

JNIEXPORT jobject JNICALL Java_com_jiminger_image_CvRasterNative__1getData(JNIEnv * env, jclass, jlong native) {
  cv::Mat* mat = (cv::Mat*) native;

  if (!mat->isContinuous()) {
    throwIllegalStateException(env, "Cannot create a CvRaster from a Mat without a continuous buffer.");
    return NULL;
  }

  jlong capacity = ((jlong)mat->total() * (jlong)mat->elemSize());

  return env->NewDirectByteBuffer(mat->ptr(0), capacity);
}

//JNIEXPORT void JNICALL Java_com_jiminger_image_CvRasterNative_showImage(JNIEnv *, jclass, jlong native) {
//  cv::Mat* mat = (cv::Mat*) native;
//  cv::imshow("Window", (*mat));
//}

JNIEXPORT jlong JNICALL Java_com_jiminger_image_CvRasterNative__1getDataAddress(JNIEnv * env, jclass, jlong native) {
  cv::Mat* mat = (cv::Mat*) native;

  if (!mat->isContinuous()) {
    throwIllegalStateException(env, "Cannot extract Mat data address form a Mat without a continuous buffer.");
    return -1;
  }

  jlong ret = (jlong) mat->ptr(0);
  return ret;
}

JNIEXPORT jlong JNICALL Java_com_jiminger_image_CvRasterNative__1copy(JNIEnv * env, jclass, jlong native) {
  cv::Mat* mat = (cv::Mat*) native;

  if (!mat->isContinuous()) {
    throwIllegalStateException(env, "Cannot copy a Mat is that Mat doesn't have a continuous buffer.");
    return -1;
  }

  cv::Mat* newMat = new cv::Mat((*mat));

  jlong ret = (jlong) newMat;
  return ret;
}

JNIEXPORT jlong JNICALL Java_com_jiminger_image_CvRasterNative__1makeMatFromRawDataReference(JNIEnv * env, jclass, jint rows, jint cols, jint type, jlong dataLong) {
  void* data = (void*) dataLong;
  cv::Mat* newMat = new cv::Mat(rows, cols, type, data);

  jlong ret = (jlong) newMat;
  return ret;
}




