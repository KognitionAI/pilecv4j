package ai.kognition.pilecv4j.gstreamer.guard;

import static org.freedesktop.gstreamer.lowlevel.GstBufferAPI.GSTBUFFER_API;

import java.nio.ByteBuffer;

import com.sun.jna.Pointer;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.lowlevel.GstBufferAPI;
import org.freedesktop.gstreamer.lowlevel.GstBufferAPI.MapInfoStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.gstreamer.VideoFrame;

public class BufferWrap extends GstWrap<Buffer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferWrap.class);

    private boolean mapped = false;
    private MapInfoStruct mapInfo = null;

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

    public VideoFrame mapToVideoFrameDecodeNow(final int rows, final int cols, final int type, final boolean writeable) {
        mapInfo = new MapInfoStruct();
        final boolean ok = GSTBUFFER_API.gst_buffer_map(obj, mapInfo,
            writeable ? GstBufferAPI.GST_MAP_WRITE : GstBufferAPI.GST_MAP_READ);
        if(ok && mapInfo.data != null) {
            return VideoFrame.create(rows, cols, type, Pointer.nativeValue(mapInfo.data), obj.getPresentationTimestamp().toNanos(),
                obj.getDecodeTimestamp().toNanos(), obj.getDuration().toNanos(), System.currentTimeMillis());
        }
        LOGGER.error("Failed to create extract a frame from the buffer.");
        return null;
    }

    public BufferWrap unmap() {
        if(mapped) {
            obj.unmap();
            mapped = false;
        }
        if(mapInfo != null) {
            GSTBUFFER_API.gst_buffer_unmap(obj, mapInfo);
            mapInfo = null;
        }
        return this;
    }

    @Override
    public void close() {
        unmap();
        super.close();
    }
}
