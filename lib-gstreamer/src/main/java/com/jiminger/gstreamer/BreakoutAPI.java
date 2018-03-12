package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Sample;
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
    Sample gst_breakout_pull_sample(BreakoutFilter appsink);
}
