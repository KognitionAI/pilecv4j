package com.jiminger.gstreamer;

import java.util.Arrays;
import java.util.List;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.lowlevel.GstAPI;
import org.freedesktop.gstreamer.lowlevel.GstNative;
import org.freedesktop.gstreamer.lowlevel.annotations.CallerOwnsReturn;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

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

    @CallerOwnsReturn
    void gst_breakout_current_frame_details(BreakoutFilter breakout, _FrameDetails details);

    public static class _FrameDetails extends com.sun.jna.Structure {
        public Pointer caps;
        public Pointer buffer;
        public int width;
        public int height;

        @Override
        protected List<String> getFieldOrder() {
            return frameDetailsFieldOrder;
        }
    }

    public static final List<String> frameDetailsFieldOrder = getFrameDetailsFieldOrder();

    public static List<String> getFrameDetailsFieldOrder() {
        try {
            return Arrays.asList(_FrameDetails.class.getField("buffer").getName(),
                    _FrameDetails.class.getField("caps").getName(),
                    _FrameDetails.class.getField("width").getName(),
                    _FrameDetails.class.getField("height").getName());
        } catch (NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

}
