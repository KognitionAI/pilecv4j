package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.lowlevel.GstAPI;
import org.freedesktop.gstreamer.lowlevel.GstNative;
import org.freedesktop.gstreamer.lowlevel.annotations.CallerOwnsReturn;

import com.sun.jna.Library;

public interface BreakoutAPI extends Library {
    static BreakoutAPI FILTER_API = GstNative.load("gstbreakout", BreakoutAPI.class);
    static int GST_PADDING = GstAPI.GST_PADDING;
    static int GST_PADDING_LARGE = GstAPI.GST_PADDING_LARGE;

    boolean gst_breakout_set_caps(BreakoutFilter appsrc, Caps incaps, Caps outcaps);

    @CallerOwnsReturn
    Buffer gst_breakout_current_frame_buffer(BreakoutFilter breakout);

    @CallerOwnsReturn
    Caps gst_breakout_current_frame_caps(BreakoutFilter breakout);

    int gst_breakout_current_frame_width(BreakoutFilter breakout);

    int gst_breakout_current_frame_height(BreakoutFilter breakout);
}
