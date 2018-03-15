package com.jiminger.gstreamer.guard;

import org.freedesktop.gstreamer.Gst;

public class GstMain implements AutoCloseable {
    private static boolean inited = false;

    public GstMain() {
        if (!inited)
            Gst.init();
        inited = true;
    }

    public GstMain(final Class<?> testClass) {
        this(testClass.getSimpleName());
    }

    public GstMain(final String appName) {
        this(appName, new String[] {});
    }

    public GstMain(final String appName, final String[] args) {
        if (!inited)
            Gst.init(appName, args);
        inited = true;
    }

    @Override
    public void close() {
        // Gst.deinit();
    }

}
