#pragma once

#include "common/jfloats.h"
#include <opencv2/core/mat.hpp>

#include <exception>
#include <cstdint>

namespace pilecv4j {
namespace ipc {

inline int* makeDepthLookup() {
  int* ret = new int[CV_DEPTH_MAX];

  ret[CV_8U] = 1;
  ret[CV_8S] = 1;
  ret[CV_16U] = 2;
  ret[CV_16S] = 2;
  ret[CV_32S] = 4;
  ret[CV_32F] = 4;
  ret[CV_64F] = 8;
  ret[CV_16F] = 2;

  return ret;
}

template<typename T> struct CvTypeFromType {
  static inline int depth() {
    throw std::exception("Unknown type to convert to an OpenCV Type");
  }
};

#define TYPE_TO_CVTYPE_MAPPING(T, CVT) \
template<> struct CvTypeFromType<T> { \
  static inline int depth() { \
    return CVT; \
  } \
}

TYPE_TO_CVTYPE_MAPPING(float32_t, CV_32F);
TYPE_TO_CVTYPE_MAPPING(float64_t, CV_64F);
TYPE_TO_CVTYPE_MAPPING(int32_t, CV_32S);
TYPE_TO_CVTYPE_MAPPING(uint16_t, CV_16U);
TYPE_TO_CVTYPE_MAPPING(int16_t, CV_16S);
TYPE_TO_CVTYPE_MAPPING(uint8_t, CV_8U);
TYPE_TO_CVTYPE_MAPPING(int8_t, CV_8S);

#undef TYPE_TO_CVTYPE_MAPPING

}
}

