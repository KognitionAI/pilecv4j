package com.jiminger.gstreamer;

import static org.freedesktop.gstreamer.lowlevel.GstBufferAPI.GSTBUFFER_API;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.elements.BaseTransform;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import org.freedesktop.gstreamer.lowlevel.GstBufferAPI;
import org.freedesktop.gstreamer.lowlevel.GstBufferAPI.MapInfoStruct;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BreakoutAPI._FrameDetails;
import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.image.CvRaster;
import com.sun.jna.Pointer;

public class BreakoutFilter extends BaseTransform {
    private static Logger LOGGER = LoggerFactory.getLogger(BreakoutFilter.class);

    public static final String GST_NAME = "breakout";
    public static final String GTYPE_NAME = "GstBreakout";

    private static final AtomicBoolean inited = new AtomicBoolean(false);

    public static void init() {
        if (!inited.getAndSet(true))
            Gst.registerClass(BreakoutFilter.class);
    }

    static {
        init();
    }

    public static final BreakoutAPI gst() {
        return BreakoutAPI.FILTER_API;
    }

    public BreakoutFilter(final Initializer init) {
        super(init);
    }

    public BreakoutFilter(final String name) {
        this(makeRawElement(GST_NAME, name));
    }

    public BreakoutFilter setCaps(final Caps incaps, final Caps outcaps) {
        gst().gst_breakout_set_caps(this, incaps, outcaps);
        return this;
    }

    @Override
    public void setCaps(final Caps caps) {
        throw new UnsupportedOperationException(
                "gstreamer element \"" + GST_NAME + "\" doesn't support the \"caps\" property. Please use a caps filter.");
    }

    public static interface SlowSampleHandler {
        public void accept(Buffer buffer, Caps caps);
    }

    private static final AtomicReference<CvRaster> storedBuffer = new AtomicReference<>();

    public static class CapsInfo implements AutoCloseable {
        public final Caps caps;
        public final int width;
        public final int height;

        protected CapsInfo(final Caps caps, final int w, final int h) {
            this.caps = caps;
            this.width = w;
            this.height = h;
        }

        @Override
        public void close() {
            if (caps != null)
                caps.dispose();
        }
    }

    public static class BufferAndCaps extends CapsInfo {
        public final Buffer buffer;
        private boolean mapped = false;
        private MapInfoStruct mapInfo = null;

        private BufferAndCaps(final Buffer frameData, final Caps caps, final int w, final int h) {
            super(caps, w, h);
            this.buffer = frameData;
        }

        public ByteBuffer map(final boolean writeable) {
            if (mapped)
                throw new IllegalStateException("You can't map the same BufferAndCaps twice.");
            mapped = true;
            final ByteBuffer ret = buffer.map(writeable);
            ret.rewind();
            return ret;
        }

        public CvRaster mapToRaster(final int type, final boolean writeable) {
            mapInfo = new MapInfoStruct();
            final boolean ok = GSTBUFFER_API.gst_buffer_map(buffer, mapInfo,
                    writeable ? GstBufferAPI.GST_MAP_WRITE : GstBufferAPI.GST_MAP_READ);
            if (ok && mapInfo.data != null) {
                return CvRaster.createManaged(height, width, type, Pointer.nativeValue(mapInfo.data));
            }
            return null;
        }

        public BufferAndCaps unmap() {
            if (mapped) {
                buffer.unmap();
                mapped = false;
            }
            if (mapInfo != null) {
                GSTBUFFER_API.gst_buffer_unmap(buffer, mapInfo);
                mapInfo = null;
            }
            return this;
        }

        @Override
        public void close() {
            unmap();
            if (buffer != null)
                buffer.dispose();
            super.close();
        }
    }

    public static class CvRasterAndCaps extends CapsInfo {
        public final CvRaster raster;
        private boolean rasterDisowned = false;

        private CvRasterAndCaps(final CvRaster frameData, final Caps caps, final int w, final int h) {
            super(caps, w, h);
            this.raster = frameData;
        }

        /**
         * Create a BufferAndCaps using a separate ByteBuffer to store the frame data. The
         * ByteBuffer that ends up as {@code buffer} will not be the ByteBuffer associated 
         * with {@code frameBuffer} but will contain a copy of the data.
         * 
         * This is primarily used to pool ByteBuffers that will outlive the scope of the call
         * back in the SlowFilterSlippage.
         */
        private CvRasterAndCaps(final Buffer frameBuffer, final CvRaster tmp, final Caps caps, final int w, final int h, final int type) {
            super(caps, w, h);
            try (BufferWrap bw = new BufferWrap(frameBuffer, false);) {
                final ByteBuffer bb = bw.map(false);
                final int capacity = bb.remaining();

                raster = (tmp != null && tmp.underlying.capacity() == capacity) ? tmp : CvRaster.createManaged(h, w, type);
                final ByteBuffer buffer = raster.underlying;

                // copy the data
                buffer.rewind();
                buffer.put(bb);
            }
        }

        @Override
        public void close() {
            if (raster != null && !rasterDisowned)
                raster.close();
        }

        private CvRaster disownRaster() {
            rasterDisowned = true;
            return raster;
        }
    }

    public BreakoutFilter connect(final Function<CvRasterAndCaps, FlowReturn> filter) {
        return connect((NEW_SAMPLE) elem -> {
            try (BufferWrap buffer = elem.getCurrentBuffer();
                    CvRaster raster = buffer.mapToRaster(elem.getCurrentFrameHeight(), elem.getCurrentFrameWidth(), CvType.CV_8UC3, true);
                    CvRasterAndCaps bac = new CvRasterAndCaps(raster, elem.getCurrentCaps(), elem.getCurrentFrameWidth(),
                            elem.getCurrentFrameHeight())) {
                return filter.apply(bac);
            }
        });
    }

    public BreakoutFilter connectSlowFilter(final Consumer<CvRasterAndCaps> filter) {
        return connect(new SlowFilterSlippage(filter));
    }

    private static class SlowFilterSlippage implements NEW_SAMPLE {
        private final Thread thread;
        private final AtomicReference<CvRasterAndCaps> to = new AtomicReference<>(null);
        private final AtomicReference<CvRasterAndCaps> result = new AtomicReference<>(null);
        private final AtomicReference<CvRasterAndCaps> current = new AtomicReference<>(null);
        private final AtomicBoolean stop = new AtomicBoolean(false);
        private boolean firstOne = true;

        public SlowFilterSlippage(final Consumer<CvRasterAndCaps> processor) {
            thread = new Thread(() -> {
                while (!stop.get()) {
                    CvRasterAndCaps frame = to.getAndSet(null);
                    while (frame == null && !stop.get()) {
                        Thread.yield();
                        frame = to.getAndSet(null);
                    }
                    if (frame != null) {
                        processor.accept(frame);
                        dispose(result.getAndSet(frame));
                    } // otherwise we're stopped.
                }
            });

            thread.setDaemon(true);
            thread.start();
        }

        private boolean fromSlow = false;

        @Override
        public FlowReturn new_sample(final BreakoutFilter elem) {
            try (final BufferWrap buffer = elem.getCurrentBuffer();) {

                // see if there's a result ready from the other thread
                final CvRasterAndCaps res = result.getAndSet(null);
                if (res != null || firstOne) { // yes. we can pass the current frame over.
                    dispose(to.getAndSet(
                            new CvRasterAndCaps(buffer.obj, storedBuffer.getAndSet(null), elem.getCurrentCaps(), elem.getCurrentFrameWidth(),
                                    elem.getCurrentFrameHeight(), CvType.CV_8UC3)));

                    if (res != null)
                        dispose(current.getAndSet(res)); // and set the current spinner on the latest result
                    firstOne = false;
                }

                final CvRasterAndCaps cur = current.get();
                if (cur == null) {
                    if (!fromSlow && LOGGER.isDebugEnabled()) {
                        fromSlow = true;
                        LOGGER.debug("Playing unprocessed stream");
                    }
                } else {
                    if (fromSlow && LOGGER.isDebugEnabled()) {
                        fromSlow = false;
                        LOGGER.debug("Playing processed stream");
                    }
                    final ByteBuffer bb = buffer.map(true);
                    final ByteBuffer curBB = cur.raster.underlying;
                    curBB.rewind();
                    bb.put(curBB);
                    // cur stays current until it's replaced, then it's disposed.
                }
            }
            return FlowReturn.OK;
        }

        public void stop() {
            stop.set(true);
        }

        private static void dispose(final CvRasterAndCaps bac) {
            if (bac != null) {
                final CvRaster old = storedBuffer.getAndSet(bac.disownRaster());
                if (old != null)
                    LOGGER.warn("Throwing away a ByteBuffer");
                bac.close();
            }
        }

    }

    // =================================================
    // Signals.
    // =================================================
    /**
     * This callback is the main filter callback. 
     */
    public static interface NEW_SAMPLE {
        public FlowReturn new_sample(BreakoutFilter elem);
    }

    public BreakoutFilter connect(final NEW_SAMPLE listener) {
        connect(NEW_SAMPLE.class, listener, new GstCallback() {
            @SuppressWarnings("unused")
            public FlowReturn callback(final BreakoutFilter elem) {
                return listener.new_sample(elem);
            }
        });
        return this;
    }
    // =================================================

    // =================================================
    // These calls should only be made from within the
    // context of the NEW_SAMPLE callback.
    // =================================================
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

    public BufferAndCaps getCurrentBufferAndCaps() {
        final _FrameDetails fd = new _FrameDetails();
        BreakoutFilter.gst().gst_breakout_current_frame_details(this, fd);
        return new BufferAndCaps(new Buffer(initializer(fd.buffer)), new Caps(initializer(fd.caps)), fd.width, fd.height);
    }
    // =================================================
}
