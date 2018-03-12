package com.jiminger.gstreamer.guard;

import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.Buffer;

public class BufferWrap extends GstWrap<Buffer> {
    private boolean mapped = false;

    public BufferWrap(final Buffer buffer) {
        this(buffer, true);
    }

    public BufferWrap(final Buffer buffer, final boolean iown) {
        super(buffer);
        disown();
    }

    public ByteBuffer map(final boolean writeable) {
        mapped = true;
        final ByteBuffer ret = obj.map(writeable);
        ret.rewind();
        return ret;
    }

    @Override
    public void close() {
        if (mapped)
            obj.unmap();
        super.close();
    }
}
