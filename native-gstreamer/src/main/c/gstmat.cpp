#include "imagemaker.h"
#include <chrono>

extern "C" {
#include "gstbreakout.h"
}

struct GstFrameData {
  GstMapInfo map;
  GstVideoFrame* frame;
};

static ai::kognition::pilecv4j::ImageMaker* imaker;

static const auto arbitraryTimeInThePast = std::chrono::steady_clock::now();

extern "C" {

  uint64_t currentTimeNanos() {
    auto finish = std::chrono::steady_clock::now();
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(finish - arbitraryTimeInThePast).count());
  }

  // exposed to java
  void set_im_maker(uint64_t im) {
    imaker = (ai::kognition::pilecv4j::ImageMaker*)im;
  }

  // NOT exposed to java
  uint64_t gst_breakout_current_frame_mat(GstBreakout* breakout, GstVideoFrame* current, void* data) {

    if (current == NULL) {
      GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
      return 0L;
    }

    int stride = GST_VIDEO_FRAME_PLANE_STRIDE (current, 0);
    int width = GST_VIDEO_FRAME_WIDTH(current);
    int height = GST_VIDEO_FRAME_HEIGHT(current);

    return imaker->makeImage(height, width, stride, data);

  }

  // NOT exposed to java
  void gst_breakout_free_gstmat(uint64_t gstmat) {
    imaker->freeImage(gstmat);
  }
}
