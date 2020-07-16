#include "imagemaker.h"

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

extern "C" {

  void set_im_maker(uint64_t im) {
    imaker = (ai::kognition::pilecv4j::ImageMaker*)im;
  }

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

    DataMapper m;
    m.vframe = current;
    m.writable = writable;

    return imaker->makeImage(height, width, stride, gstFrameDataStructSize, &m);
  }

  void gst_breakout_current_frame_mat_unmap(uint64_t gstmat) {
    GstFrameData* fd = (GstFrameData*)imaker->userdata(gstmat);
    gst_buffer_unmap(fd->frame->buffer, &(fd->map));
  }

}
