#pragma once

#include <cstdint>

#include <stdlib.h>
#include <stdint.h>

namespace ai {
  namespace kognition {
    namespace pilecv4j {

      struct MatAndData {
        uint64_t mat;
        void* data;
      };

      /**
       * This coresponds to various CV_XXXX formats.
       */
      enum PixelFormat {
        UNKNOWN,
        RGB24,
        BGR24
      };

      struct RawRaster {
        uint8_t* data;
        PixelFormat pixFormat;
        int w;
        int h;
        size_t stride; // bytes per row
      };

      class ImageMaker {
      public:
        virtual ~ImageMaker() = default;

        virtual uint64_t makeImage(int height, int width, int stride, void* data) = 0;

        virtual MatAndData allocateImage(int height, int width) = 0;

        virtual uint64_t allocateImageWithCopyOfData(int height, int width, int stride, void* data) = 0;

        virtual uint64_t allocateImageWithData(int height, int width, int stride, void* data) = 0;

        virtual void freeImage(uint64_t mat) = 0;

        virtual uint64_t copy(uint64_t mat) = 0;

        virtual bool extractImageDetails(uint64_t matRef, bool isRgb, RawRaster* rasterToFill) = 0;
      };
    }
  }
}

