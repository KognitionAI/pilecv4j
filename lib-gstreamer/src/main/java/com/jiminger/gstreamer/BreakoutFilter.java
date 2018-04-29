package com.jiminger.gstreamer;

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
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.image.CvRaster;

public class BreakoutFilter extends BaseTransform {
    private static Logger LOGGER = LoggerFactory.getLogger(BreakoutFilter.class);

    public static final String GST_NAME = "breakout";
    public static final String GTYPE_NAME = "GstBreakout";

    private static final AtomicBoolean inited = new AtomicBoolean(false);

    private SlowFilterSlippage slowFilterSlippage = null;

    private static final BreakoutAPI FILTER_API = BreakoutAPI.FILTER_API;

    public static void init() {
        if (!inited.getAndSet(true)) {
            Gst.registerClass(BreakoutFilter.class);
        }
    }

    static {
        init();
    }

    public static final BreakoutAPI gst() {
        return FILTER_API;
    }

    public BreakoutFilter(final Initializer init) {
        super(init);
    }

    public BreakoutFilter(final String name) {
        this(makeRawElement(GST_NAME, name));
    }

    @Override
    public void setCaps(final Caps caps) {
        throw new UnsupportedOperationException(
                "gstreamer element \"" + GST_NAME + "\" doesn't support the \"caps\" property. Please use a caps filter.");
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
            super.close();
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
        final BreakoutFilter ret = connect(slowFilterSlippage = new SlowFilterSlippage(filter));
        return ret;
    }

    @Override
    public void dispose() {
        if (slowFilterSlippage != null) {
            slowFilterSlippage.stop();
            try {
                slowFilterSlippage.thread.join(1000);
            } catch (final InterruptedException ie) {
                LOGGER.info("Interrupted while waiting for slow filter slippage thread for " + this.getName() + " to exit.");
            }

            if (slowFilterSlippage.thread.isAlive())
                LOGGER.warn("The slow filter slippage thread for " + this.getName() + " never exited. Moving on.");
        }

        super.dispose();
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
                    if (frame != null && !stop.get()) {
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

    // public BufferAndCaps getCurrentBufferAndCaps() {
    // final _FrameDetails fd = new _FrameDetails();
    // BreakoutFilter.gst().gst_breakout_current_frame_details(this, fd);
    // return new BufferAndCaps(new Buffer(initializer(fd.buffer)), new Caps(initializer(fd.caps)), fd.width, fd.height);
    // }
    // =================================================
}
