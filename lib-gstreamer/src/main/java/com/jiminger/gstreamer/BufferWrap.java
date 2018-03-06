package com.jiminger.gstreamer;

import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.Buffer;

public class BufferWrap extends GstWrap<Buffer> {
    private boolean mapped = false;

    public BufferWrap(final Buffer buffer) {
        super(buffer);
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
