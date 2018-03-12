package com.jiminger.gstreamer;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.elements.BaseTransform;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;

import com.jiminger.gstreamer.guard.BufferWrap;

public class BreakoutFilter extends BaseTransform {
    public static final String GST_NAME = "breakout";
    public static final String GTYPE_NAME = "GstBreakout";

    private static final AtomicBoolean inited = new AtomicBoolean(false);

    public static void init() {
        if (!inited.getAndSet(true))
            Gst.registerClass(BreakoutFilter.class);
    }

    public static final BreakoutAPI gst() {
        return BreakoutAPI.FILTER_API;
    }

    public BreakoutFilter(final Initializer init) {
        super(init);
        System.out.println("Making it!");
    }

    public BreakoutFilter(final String name) {
        this(makeRawElement(GST_NAME, name));
    }

    public static interface NEW_SAMPLE {
        public void new_sample(BreakoutFilter elem);
    }

    public BufferWrap getCurrentBuffer() {
        return new BufferWrap(gst().gst_breakout_current_frame_buffer(this), false);
    }

    public Caps getCurrentCaps() {
        return gst().gst_breakout_current_frame_caps(this);
    }

    public int getCurrentFrameWidth() {
        return gst().gst_breakout_current_frame_width(this);
    }

    public int getCurrentFrameHeight() {
        return gst().gst_breakout_current_frame_height(this);
    }

    public void connect(final NEW_SAMPLE listener) {
        connect(NEW_SAMPLE.class, listener, new GstCallback() {
            @SuppressWarnings("unused")
            public void callback(final BreakoutFilter elem) {
                listener.new_sample(elem);
            }
        });
    }

}
