package com.jiminger.gstreamer.guard;

import org.freedesktop.gstreamer.Gst;

public class GstMain implements AutoCloseable {

    public GstMain() {
        Gst.init();
    }

    public GstMain(final Class<?> testClass) {
        this(testClass.getSimpleName());
    }

    public GstMain(final String appName) {
        this(appName, new String[] {});
    }

    public GstMain(final String appName, final String[] args) {
        Gst.init(appName, args);
    }

    @Override
    public void close() {
        Gst.deinit();
    }

}
