package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.MiniObject;

public class GstWrap<T extends MiniObject> implements AutoCloseable {
    public final T obj;
    private boolean disowned = false;

    public GstWrap(final T obj) {
        this.obj = obj;
    }

    public T disown() {
        disowned = true;
        return obj;
    }

    @Override
    public void close() {
        if (!disowned && obj != null)
            obj.dispose();
    }
}
