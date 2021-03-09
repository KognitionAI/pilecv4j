#pragma once

#include <cstdint>

/**
 * NOTE: THIS FILE MUST BE IDENTICAL BETWEEN native-gstreamer AND native-image.
 * TODO: Make this file shared.
 */
namespace ai {
  namespace kognition {
    namespace pilecv4j {

      class ImageMaker {
      public:
        virtual ~ImageMaker() {}

        virtual uint64_t makeImage(int height, int width, int stride, void* data) = 0;

        virtual void freeImage(uint64_t mat) = 0;

        virtual uint64_t copy(uint64_t mat) = 0;
      };
    }
  }
}

