#include "imagemaker.h"
#include <stdio.h>
#include <chrono>

extern "C" {
#include "gstbreakout.h"
}

struct GstFrameData {
  GstMapInfo map;
  GstVideoFrame* frame;
};

static const std::size_t gstFrameDataStructSize = sizeof(GstFrameData);
static ai::kognition::pilecv4j::ImageMaker* imaker;

class DataMapper : public ai::kognition::pilecv4j::DataMapper {
public:
  GstVideoFrame* vframe;
  bool writable;

  inline void* mapData(void* alignedStruct) {
    GstFrameData* vfd = (GstFrameData*)alignedStruct;
    vfd->frame = vframe;
    gst_buffer_map (vframe->buffer, &(vfd->map), writable ? GST_MAP_WRITE : GST_MAP_READ);
    return vfd->map.data;
  }
};

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
  uint64_t gst_breakout_current_frame_mat(GstBreakout* breakout, int writable, GstVideoFrame* current) {

    if (current == NULL) {
      GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
      return 0L;
    }

    int stride = GST_VIDEO_FRAME_PLANE_STRIDE (current, 0);
    int width = GST_VIDEO_FRAME_WIDTH(current);
    int height = GST_VIDEO_FRAME_HEIGHT(current);

    DataMapper m;
    m.vframe = current;
    m.writable = writable;

    uint64_t ret = imaker->makeImage(height, width, stride, gstFrameDataStructSize, &m);
    return ret;

  }

  // NOT exposed to java
  void gst_breakout_current_frame_mat_unmap(uint64_t gstmat) {
    GstFrameData* fd = (GstFrameData*)imaker->userdata(gstmat);
    gst_buffer_unmap(fd->frame->buffer, &(fd->map));
  }

  // NOT exposed to java
  void gst_breakout_free_gstmat(uint64_t gstmat) {
    imaker->freeImage(gstmat);
  }

  // NOT exposed to java
  uint64_t gst_breakout_copy_gstmat(uint64_t gstmat) {
    return imaker->copy(gstmat);
  }
}
