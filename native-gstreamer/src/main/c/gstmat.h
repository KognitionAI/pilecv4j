
#ifndef _GST_MAT_H_
#define _GST_MAT_H_
#include <stdint.h>

struct GstBreakout;

uint64_t gst_breakout_current_frame_mat(GstBreakout* breakout, GstVideoFrame* current, void* data);
void gst_breakout_free_gstmat(uint64_t gstmat);
uint64_t currentTimeNanos();

#endif
