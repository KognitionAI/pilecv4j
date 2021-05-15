#pragma once

#include <cstdint>

namespace ai {
  namespace kognition {
    namespace pilecv4j {

      struct MatAndData {
        uint64_t mat;
        void* data;
      };

      class ImageMaker {
      public:
        virtual ~ImageMaker() {}

        virtual uint64_t makeImage(int height, int width, int stride, void* data) = 0;

        virtual MatAndData allocateImage(int height, int width) = 0;

        virtual uint64_t allocateImageWithCopyOfData(int height, int width, int stride, void* data) = 0;

        virtual void freeImage(uint64_t mat) = 0;

        virtual uint64_t copy(uint64_t mat) = 0;
      };
    }
  }
}

