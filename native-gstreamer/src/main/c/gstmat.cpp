#include <opencv2/core/mat.hpp>

extern "C" {
#include "gstbreakout.h"
}

static void* mapData(GstMapInfo* map, GstVideoFrame* vframe, bool writable) {
  gst_buffer_map (vframe->buffer, map, writable ? GST_MAP_WRITE : GST_MAP_READ);
  return map->data;
}

struct GstMat : public cv::Mat {
  // This is a c struct and has no constructor ... otherwise this wouldn't work
  // because the order of operations would be:
  //    1) call mapData
  //    2) super class initialized
  //    3) member initialization (including map which would be reset).
  GstMapInfo map;
  GstVideoFrame* frame;

  inline GstMat(int height, int width, int stride, int type, GstVideoFrame* vframe, bool writable) :
    cv::Mat(height, width, type, mapData(&map, vframe, writable), stride), frame(vframe) {
  }
};

extern "C" {

  uint64_t gst_breakout_current_frame_mat(uint64_t me, bool writable) {
    GstBreakout* breakout = (GstBreakout*)me;

    GstVideoFrame* current = breakout->cur;
    if (current == NULL) {
      GST_WARNING_OBJECT (breakout, "there is no frame at this point. The method should be called from within a callback. Return NULL");
      return 0L;
    }

    int stride = GST_VIDEO_FRAME_PLANE_STRIDE (current, 0);
    int width = GST_VIDEO_FRAME_WIDTH(current);
    int height = GST_VIDEO_FRAME_HEIGHT(current);

    GstMat* rret = new GstMat(height, width,stride, CV_8UC3, current, writable );
    uint64_t ret = (uint64_t) rret;

    cv::Mat* cvmat = rret;
    return (uint64_t)cvmat;
  }

  void gst_breakout_current_frame_mat_unmap(long gstmat) {
    cv::Mat* cvmat = (cv::Mat*)gstmat;
    GstMat* it = static_cast<GstMat*>(cvmat);
    gst_buffer_unmap(it->frame->buffer, &(it->map));
  }

}
