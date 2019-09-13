package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.elements.BaseTransform;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.gstreamer.VideoFrame.Pool;
import ai.kognition.pilecv4j.gstreamer.guard.BufferWrap;

public class BreakoutFilter extends BaseTransform {
    private static Logger LOGGER = LoggerFactory.getLogger(BreakoutFilter.class);

    public static final String GST_NAME = "breakout";
    public static final String GTYPE_NAME = "GstBreakout";

    private static final AtomicBoolean inited = new AtomicBoolean(false);

    private final List<VideoFrameFilter> filterStack = new ArrayList<>();

    private static final BreakoutAPI FILTER_API = BreakoutAPI.FILTER_API;

    public static void init() {
        if(!inited.getAndSet(true)) {
            Gst.registerClass(BreakoutFilter.class);
        }
    }

    static {
        init();
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

    public static class VideoFrameFilterException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public final FlowReturn flowReturn;

        public VideoFrameFilterException(final FlowReturn flowReturn) {
            this.flowReturn = flowReturn;
        }
    }

    @FunctionalInterface
    public static interface VideoFrameFilter extends Consumer<CvMatAndCaps>, QuietCloseable {

        @Override
        default void close() {}
    }

    private static abstract class ProxyFilter implements VideoFrameFilter {
        protected final List<Thread> threads = new ArrayList<>();
        protected final AtomicBoolean stop = new AtomicBoolean(false);
        private final AtomicReference<VideoFrame.Pool> storedBuffers = new AtomicReference<>();

        @Override
        public void close() {
            stop.set(true);

            try {
                Functional.<InterruptedException>recheck(() -> threads.forEach(t -> uncheck(() -> t.join(1000))));
            } catch(final InterruptedException ie) {
                LOGGER.info("Interrupted while waiting for asynchronous filter slippage thread to exit.");
            }

            threads.forEach(t -> {
                if(t.isAlive())
                    LOGGER.warn("The asynchronous filter slippage thread never exited. Moving on.");
            });

            final Pool pool = storedBuffers.getAndSet(null);
            if(pool != null)
                pool.close();
        }

        VideoFrame.Pool getPool(final VideoFrame frame) {
            return storedBuffers.updateAndGet(p -> {
                if(p == null)
                    return VideoFrame.getPool(frame.rows(), frame.cols(), frame.type());
                return p;
            });
        }
    }

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
            if(caps != null)
                caps.dispose();
        }

        @Override
        public String toString() {
            return "CapsInfo [caps=" + caps + ", width=" + width + ", height=" + height + "]";
        }
    }

    public static class CvMatAndCaps extends CapsInfo {
        public final VideoFrame mat;
        public final boolean iOwnMat;

        private CvMatAndCaps(final VideoFrame frameData, final Caps caps, final boolean youOwnMat) {
            super(caps, frameData.width(), frameData.height());
            this.mat = frameData;
            this.iOwnMat = youOwnMat;
        }

        @Override
        public void close() {
            if(iOwnMat && mat != null)
                mat.close();

            super.close();
        }

        @Override
        public String toString() {
            return "CvRasterAndCaps [raster=" + mat + /* ", rasterDisowned=" + rasterDisowned + */ ", caps=" + caps + ", width=" + width
                + ", height=" + height + "]";
        }
    }

    public BreakoutFilter filter(final VideoFrameFilter filter) {
        return doConnect(filter);
    }

    public BreakoutFilter slowFilter(final Consumer<CvMatAndCaps> filter) {
        return slowFilter(1, filter);
    }

    public BreakoutFilter slowFilter(final int numThreads, final Consumer<CvMatAndCaps> filter) {
        final BreakoutFilter ret = doConnect(new SlowFilterSlippage(numThreads, filter));
        return ret;
    }

    /**
     * A "stream watcher" as opposed to a "filter" can have no affect on the frames flowing through the
     * pipeline. It receives frames in the form of a {@link CvMatAndCaps} but any changes to the
     * {@link VideoFrame} will not be put back into the pipeline.
     */
    public BreakoutFilter streamWatcher(final int numThreads, final Supplier<Consumer<CvMatAndCaps>> watcherSupplier) {
        final BreakoutFilter ret = doConnect(new StreamWatcher(numThreads, watcherSupplier));
        return ret;
    }

    /**
     * This is the an alternate means of supplying a "stream watcher" (see {@link #streamWatcher} for an
     * explanation of the difference between a "stream watcher" and a "filter"). In this case the {@code watcherSupplier}
     * will be invoked on the first frame allowing initialization of the stream watcher to be done separate from the
     * {@code Consumer<CvMatAndCaps>}.
     */
    public BreakoutFilter delayedStreamWatcher(final int numThreads,
        final Function<CvMatAndCaps, Supplier<Consumer<CvMatAndCaps>>> watcherSupplier) {
        doConnect(new VideoFrameFilter() {

            @Override
            public void accept(final CvMatAndCaps mac) {
                doDisconnect(this);
                streamWatcher(numThreads, watcherSupplier.apply(mac));
            }
        });
        return this;
    }

    @Override
    public void dispose() {
        for(int i = filterStack.size() - 1; i >= 0; i--) {
            final VideoFrameFilter proxyFilter = filterStack.get(i);
            if(proxyFilter != null)
                proxyFilter.close();
        }
        super.dispose();
    }

    // =================================================
    // Signals.
    // =================================================
    /**
     * This callback is the main filter callback.
     */
    private static interface NEW_SAMPLE {
        public FlowReturn new_sample(BreakoutFilter elem);
    }

    private final NEW_SAMPLE actualFilter = new NEW_SAMPLE() {
        @Override
        public FlowReturn new_sample(final BreakoutFilter elem) {
            try (final BufferWrap buffer = elem.getCurrentBuffer();
                BufferWrap bw = new BufferWrap(buffer.obj, true);
                final VideoFrame mat = bw.mapToVideoFrameDecodeNow(elem.getCurrentFrameHeight(), elem.getCurrentFrameWidth(), CvType.CV_8UC3, true);) {
                final CvMatAndCaps ctx = new CvMatAndCaps(mat, elem.getCurrentCaps(), false);
                filterStack.forEach(f -> f.accept(ctx));
                return FlowReturn.OK;
            } catch(final VideoFrameFilterException vffe) {
                LOGGER.error("Unexpected processing exception: {}", vffe.flowReturn, vffe);
                return vffe.flowReturn;
            } catch(final Exception e) {
                LOGGER.error("Unexpected processing exception:", e);
                return FlowReturn.ERROR;
            }
        }
    };

    private synchronized BreakoutFilter doConnect(final VideoFrameFilter listener) {
        if(filterStack.size() == 0)
            connect(NEW_SAMPLE.class, actualFilter, new GstCallback() {
                @SuppressWarnings("unused")
                public FlowReturn callback(final BreakoutFilter elem) {
                    return actualFilter.new_sample(elem);
                }
            });
        push(listener);
        return this;
    }

    private synchronized BreakoutFilter doDisconnect(final VideoFrameFilter listener) {
        if(filterStack.removeIf(l -> l == listener)) {
            if(listener instanceof ProxyFilter)
                ((ProxyFilter)listener).close();
        }
        if(filterStack.size() == 0)
            disconnect(NEW_SAMPLE.class, actualFilter);
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
    // =================================================

    private static final BreakoutAPI gst() {
        return FILTER_API;
    }

    private synchronized VideoFrameFilter push(final VideoFrameFilter pf) {
        filterStack.add(pf);
        return pf;
    }

    private static class SlowFilterSlippage extends ProxyFilter {
        private final AtomicReference<CvMatAndCaps> to = new AtomicReference<>(null);
        private final AtomicReference<CvMatAndCaps> result = new AtomicReference<>(null);
        private CvMatAndCaps current = null;
        private boolean firstOne = true;

        private static AtomicLong threadSequence = new AtomicLong(0);

        private void dispose(final CvMatAndCaps bac) {
            if(bac != null) {
                bac.close();
            }
        }

        private Runnable fromProcessor(final Consumer<CvMatAndCaps> processor) {
            return () -> {
                while(!stop.get()) {
                    CvMatAndCaps frame = to.getAndSet(null);
                    while(frame == null && !stop.get()) {
                        Thread.yield();
                        frame = to.getAndSet(null);
                    }
                    if(frame != null && !stop.get()) {
                        try {
                            processor.accept(frame);
                        } catch(final Exception exc) {
                            if(LOGGER.isDebugEnabled())
                                LOGGER.info("User supplied processor threw exception: {}", exc.getLocalizedMessage(), exc);
                            else
                                LOGGER.info("User supplied processor threw exception: {} (to see stack trace enable debug logging for {})",
                                    exc.getLocalizedMessage(), BreakoutFilter.class.getName());
                        }
                        dispose(result.getAndSet(frame));
                    } // otherwise we're stopped.
                }
            };
        }

        public SlowFilterSlippage(final int numThreads, final Consumer<CvMatAndCaps> processor) {
            for(int i = 0; i < numThreads; i++)
                threads.add(chain(new Thread(fromProcessor(processor), nextThreadName()), t -> t.setDaemon(true), t -> t.start()));
        }

        private String nextThreadName() {
            return SlowFilterSlippage.class.getSimpleName() + "-thread-" + threadSequence.getAndIncrement();
        }

        // These are for debug logs only
        private boolean fromSlow = false;
        private CvMatAndCaps prevCurrent = null;

        @Override
        public void accept(final CvMatAndCaps frameAndCaps) {
            final VideoFrame frame = frameAndCaps.mat;
            // ========================================================
            // The goal here is to set 'current' with the result from
            // the other thread. Otherwise, if we have no result yet,
            // leave it alone to indicate that.
            //
            // At first 'current' will be null indicating no results
            // have been sent back from the 'processor' yet.
            //
            // ========================================================
            // see if there's a result ready from the other thread
            final CvMatAndCaps res = result.getAndSet(null);
            if(res != null || firstOne) { // yes. we can pass the current frame over.
                dispose(to.getAndSet(
                    new CvMatAndCaps(frame.pooledDeepCopy(getPool(frame)), frameAndCaps.caps, true)));

                if(res != null) {
                    dispose(current); // and set the current spinner on the latest result
                    current = res;
                }
                firstOne = false;
            }
            // ========================================================

            // Have we seen ANY results from the processor yet?
            if(current == null) { // no, we haven't seen results from the processor
                if(!fromSlow && LOGGER.isDebugEnabled()) {
                    fromSlow = true;
                    LOGGER.debug("Playing unprocessed stream");
                }
            } else { // yes, we've seen results from the processor
                if(fromSlow && LOGGER.isDebugEnabled()) {
                    fromSlow = false;
                    LOGGER.debug("Playing processed stream");
                }

                if(LOGGER.isTraceEnabled()) {
                    if(current == prevCurrent) {
                        LOGGER.trace("Slipping. Sending same frame.");
                    }
                    prevCurrent = current;
                }

                current.mat.copyTo(frame);
            }
        }

    }

    private static class StreamWatcher extends ProxyFilter {
        private final AtomicReference<CvMatAndCaps> to = new AtomicReference<>(null);
        private final AtomicLong sequence = new AtomicLong(0);

        private void dispose(final CvMatAndCaps toDispose) {
            if(toDispose != null)
                toDispose.close();
        }

        private Runnable fromProcessor(final Consumer<CvMatAndCaps> processor) {
            return () -> {
                while(!stop.get()) {
                    CvMatAndCaps frame = to.getAndSet(null);
                    while(frame == null && !stop.get()) {
                        Thread.yield();
                        frame = to.getAndSet(null);
                    }
                    if(frame != null && !stop.get()) {
                        try {
                            processor.accept(frame);
                        } catch(final Exception exc) {
                            if(LOGGER.isDebugEnabled())
                                LOGGER.info("User supplied from processor threw exception: {}", exc.getLocalizedMessage(), exc);
                            else
                                LOGGER.info("User supplied from processor threw exception: {} (to see stack trace enable debug logging for {})",
                                    exc.getLocalizedMessage(), BreakoutFilter.class.getName());
                        }
                        dispose(frame);
                    } // otherwise we're stopped.
                }
            };
        }

        public StreamWatcher(final int numThreads, final Supplier<Consumer<CvMatAndCaps>> processorSupplier) {
            for(int i = 0; i < numThreads; i++)
                threads.add(chain(new Thread(fromProcessor(processorSupplier.get()), "Stream Watcher " + sequence.getAndIncrement()), t -> t.setDaemon(true),
                    t -> t.start()));
        }

        @Override
        public void accept(final CvMatAndCaps frameAndCaps) {
            final VideoFrame frame = frameAndCaps.mat;
            dispose(to.updateAndGet(mac -> (mac == null) ? new CvMatAndCaps(frame.pooledDeepCopy(getPool(frame)), frameAndCaps.caps, true) : mac));
        }
    }
}
