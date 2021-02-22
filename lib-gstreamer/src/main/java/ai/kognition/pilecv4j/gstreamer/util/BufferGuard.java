package ai.kognition.pilecv4j.gstreamer.util;

import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.Buffer;

import net.dempsy.util.QuietCloseable;

/**
 * This class guarantees that mapped buffers are unmapped eitehr on
 * close or on disowning.
 */
public class BufferGuard implements QuietCloseable {
    private Buffer ref;
    private boolean mapped = false;

    public BufferGuard(final Buffer buf) {
        this.ref = buf;
    }

    public Buffer disownAndUnmapIfNecessary() {
        if(mapped)
            unmap();
        final Buffer ret = ref;
        ref = null;
        return ret;
    }

    public ByteBuffer map(final boolean writable) {
        mapped = true;
        return ref.map(writable);
    }

    public void unmap() {
        mapped = false;
        ref.unmap();
    }

    @Override
    public void close() {
        if(ref != null) {
            if(mapped)
                unmap();
            ref.close();
        }
        ref = null;
    }
}
