package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.elements.BaseTransform;
import org.freedesktop.gstreamer.glib.NativeObject;
import org.freedesktop.gstreamer.glib.Natives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;
import net.dempsy.util.executor.AutoDisposeSingleThreadScheduler;
import net.dempsy.util.executor.AutoDisposeSingleThreadScheduler.Cancelable;

import ai.kognition.pilecv4j.gstreamer.internal.BreakoutAPI;
import ai.kognition.pilecv4j.gstreamer.internal.BreakoutAPIRaw;
import ai.kognition.pilecv4j.image.VideoFrame;
import ai.kognition.pilecv4j.image.VideoFrame.Pool;

public class BreakoutFilter extends BaseTransform {
    private static Logger LOGGER = LoggerFactory.getLogger(BreakoutFilter.class);
    private static final boolean traceOn = LOGGER.isTraceEnabled();

    public static final String GST_NAME = "breakout";
    public static final String GTYPE_NAME = "GstBreakout";

    private final List<VideoFrameFilter> filterStack = new ArrayList<>();

    private static final BreakoutAPI FILTER_API = BreakoutAPI.FILTER_API;
    private BreakoutAPIRaw.push_frame_callback actualFilter = null;

    public static class BreakoutFilterTypeProvider implements NativeObject.TypeProvider {
        @Override
        public Stream<TypeRegistration<?>> types() {
            return Stream.of(Natives.registration(BreakoutFilter.class, GTYPE_NAME, BreakoutFilter::new));
        }
    }

    private final AtomicLong frameNumber = new AtomicLong(0);

    static void initFromScope() {}

    private final long me;

    public BreakoutFilter(final Initializer init) {
        super(init);
        me = gst().who_am_i(this);
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
    public static interface VideoFrameFilter extends Consumer<VideoFrame>, QuietCloseable {

        @Override
        default void close() {}
    }

    public BreakoutFilter filter(final VideoFrameFilter filter) {
        return doConnect(filter, true);
    }

    public BreakoutFilter watch(final VideoFrameFilter filter) {
        return doConnect(filter, false);
    }

    public BreakoutFilter slowFilter(final VideoFrameFilter filter) {
        return slowFilter(1, filter);
    }

    public BreakoutFilter slowFilter(final int numThreads, final VideoFrameFilter filter) {
        return doDelayedConnect(mac -> new SlowFilterSlippage(mac, numThreads, filter), true);
    }

    private final static AutoDisposeSingleThreadScheduler dataWatcher = new AutoDisposeSingleThreadScheduler("Frame Watchdog");

    public BreakoutFilter addWatchdog(final long noDataTimeoutMillis, final Runnable timeoutAction) {
        final AtomicLong timeOfLastFrame = new AtomicLong(0L);

        final AtomicReference<Cancelable> cancelable = new AtomicReference<>(null);
        cancelable.set(
            dataWatcher.schedule(
                // This is an anonymous class rather than a lambda because it reschedules itself
                new Runnable() {
                    boolean tookAction = false;

                    @Override
                    public void run() {
                        final long curTime = System.currentTimeMillis();
                        final long last = timeOfLastFrame.get();
                        try {
                            if((curTime - last) > noDataTimeoutMillis) {
                                LOGGER.error("No Data received for {} milliseconds. EXITING!", curTime - last);
                                tookAction = true; // in case timeoutAction.run throws we do this first
                                timeoutAction.run();
                            }
                        } catch(final Throwable th) {
                            LOGGER.error("Unepxected error", th);
                        }
                        synchronized(cancelable) {
                            if(cancelable.get() != null && !tookAction) // otherwise it's an indication we're done. It's set to null after all frames have
                                                                        // been processed.
                                cancelable.set(
                                    // wake me up before you go go ...
                                    dataWatcher.schedule(this, noDataTimeoutMillis - (curTime - last) + 10, TimeUnit.MILLISECONDS));
                        }
                    }
                }, noDataTimeoutMillis, TimeUnit.MILLISECONDS));

        watch(new VideoFrameFilter() {
            private boolean isClosed = false;

            @Override
            public void accept(final VideoFrame cv) {
                timeOfLastFrame.set(System.currentTimeMillis());
            }

            @Override
            public void close() {
                synchronized(cancelable) {
                    if(!isClosed) {
                        final Cancelable toCancel = cancelable.getAndSet(null);
                        toCancel.cancel();
                        isClosed = true;
                    }
                }
            }
        });
        return this;
    }

    /**
     * This is the an alternate means of supplying a "stream watcher" (see {@link #streamWatcher} for an
     * explanation of the difference between a "stream watcher" and a "filter"). In this case the {@code watcherSupplier}
     * will be invoked on the first frame allowing initialization of the stream watcher to be done separate from the
     * {@code Consumer<CvMatAndCaps>}.
     */
    public BreakoutFilter delayedSlowFilter(final int numThreads, final Function<VideoFrame, VideoFrameFilter> watcherSupplier) {
        return doDelayedConnect(mac -> new SlowFilterSlippage(mac, numThreads, watcherSupplier.apply(mac)), true);
    }

    /**
     * A "stream watcher" as opposed to a "filter" can have no affect on the frames flowing through the
     * pipeline. It receives frames in the form of a {@link CvMatAndCaps} but any changes to the
     * {@link VideoFrame} will not be put back into the pipeline.
     */
    public BreakoutFilter streamWatcher(final VideoFrameFilter watcher) {
        return streamWatcher(1, watcher);
    }

    /**
     * A "stream watcher" as opposed to a "filter" can have no affect on the frames flowing through the
     * pipeline. It receives frames in the form of a {@link CvMatAndCaps} but any changes to the
     * {@link VideoFrame} will not be put back into the pipeline.
     */
    public BreakoutFilter streamWatcher(final int numThreads, final VideoFrameFilter watcher) {
        return doDelayedConnect(mac -> new StreamWatcher(mac, numThreads, watcher), false);
    }

    /**
     * This is the an alternate means of supplying a "stream watcher" (see {@link #streamWatcher} for an
     * explanation of the difference between a "stream watcher" and a "filter"). In this case the {@code watcherSupplier}
     * will be invoked on the first frame allowing initialization of the stream watcher to be done separate from the
     * {@code Consumer<CvMatAndCaps>}.
     */
    public BreakoutFilter delayedStreamWatcher(final int numThreads, final Function<VideoFrame, VideoFrameFilter> watcherSupplier) {
        return doDelayedConnect(mac -> new StreamWatcher(mac, numThreads, watcherSupplier.apply(mac)), false);
    }

    @Override
    public synchronized void dispose() {
        for(int i = filterStack.size() - 1; i >= 0; i--) {
            final VideoFrameFilter proxyFilter = filterStack.get(i);
            if(proxyFilter != null)
                proxyFilter.close();
        }
        super.dispose();
    }

    public synchronized BreakoutFilter disconnect(final VideoFrameFilter listener) {
        if(filterStack.removeIf(l -> l == listener)) {
            if(listener instanceof ProxyFilter)
                ((ProxyFilter)listener).close();
        }
        if(filterStack.size() == 0) {
            BreakoutAPIRaw.set_push_frame_callback(me, null, 0);
        }
        return this;
    }

    private static final BreakoutAPI gst() {
        return FILTER_API;
    }

    private static abstract class ProxyFilter implements VideoFrameFilter {
        protected final List<Thread> threads = new ArrayList<>();
        protected final AtomicBoolean stop = new AtomicBoolean(false);
        private final AtomicReference<VideoFrame.Pool> storedBuffers = new AtomicReference<>();

        @Override
        public void close() {
            stop.set(true);

            boolean done = false;
            for(int i = 0; i < 1000 && !done; i++) {
                // interrupt each thread.
                threads.stream().forEach(t -> t.interrupt());
                done = !threads.stream().anyMatch(t -> t.isAlive());

                if(!done) {
                    try {
                        Functional.<InterruptedException>recheck(() -> uncheck(() -> Thread.sleep(1)));
                    } catch(final InterruptedException ie) {
                        LOGGER.info("Interrupted while waiting for asynchronous filter slippage thread to exit.");
                    }
                }
            }

            threads.forEach(t -> {
                if(t.isAlive())
                    LOGGER.warn("The asynchronous filter slippage thread never exited. Moving on.");
            });

            final Pool pool = storedBuffers.getAndSet(null);
            if(pool != null)
                pool.close();

            cleanup();
        }

        protected void cleanup() {}

        private void initPool(final VideoFrame frame) {
            storedBuffers.set(VideoFrame.getPool(frame.rows(), frame.cols(), frame.type(), frame.isRgb));
        }

        VideoFrame.Pool getPool(final VideoFrame frame) {
            return storedBuffers.get();
        }
    }

    private static class SlowFilterSlippage extends ProxyFilter {
        private final AtomicReference<VideoFrame> toBeProcessed = new AtomicReference<>(null);
        private final AtomicReference<VideoFrame> result = new AtomicReference<>(null);
        private VideoFrame current = null;
        private boolean firstOne = true;

        private static AtomicLong threadSequence = new AtomicLong(0);

        @Override
        protected void cleanup() {
            dispose(toBeProcessed.getAndSet(null));
            dispose(result.getAndSet(null));
        }

        private void dispose(final VideoFrame bac) {
            if(bac != null) {
                bac.close();
            }
        }

        private Runnable fromProcessor(final Consumer<VideoFrame> processor) {
            return () -> {
                while(!stop.get()) {
                    VideoFrame frame = toBeProcessed.getAndSet(null);
                    while(frame == null && !stop.get()) {
                        Thread.yield();
                        frame = toBeProcessed.getAndSet(null);
                    }
                    if(frame != null && !stop.get()) {
                        try {
                            processor.accept(frame);
                        } catch(final Exception exc) {
                            if(LOGGER.isDebugEnabled())
                                LOGGER.info("SlowFilter: User supplied processor threw exception: {}", exc.getLocalizedMessage(), exc);
                            else
                                LOGGER.info("SlowFilter: User supplied processor threw exception: {} (to see stack trace enable debug logging for {})",
                                    exc.getLocalizedMessage(), BreakoutFilter.class.getName());
                        }
                        dispose(result.getAndSet(frame));
                    } // otherwise we're stopped.
                }
            };
        }

        private SlowFilterSlippage(final VideoFrame frame, final int numThreads, final VideoFrameFilter processor) {
            super.initPool(frame);
            for(int i = 0; i < numThreads; i++)
                threads.add(chain(new Thread(fromProcessor(processor), nextThreadName()), t -> t.setDaemon(true), t -> t.start()));
        }

        private String nextThreadName() {
            return SlowFilterSlippage.class.getSimpleName() + "-thread-" + threadSequence.getAndIncrement();
        }

        // These are for debug logs only
        private boolean fromSlow = false;
        private VideoFrame prevCurrent = null;

        @Override
        public void accept(final VideoFrame frame) {
            if(stop.get())
                return;

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
            final VideoFrame res = result.getAndSet(null);
            if(res != null || firstOne) { // yes. we can pass the current frame over.
                dispose(toBeProcessed.getAndSet(frame.pooledDeepCopy(getPool(frame))));

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

                current.copyTo(frame);
            }
        }
    }

    static class PseudoAtomicReference<T> {
        public T ref;

        public PseudoAtomicReference(final T o) {
            ref = o;
        }

        public T getAndSet(final T newVal) {
            synchronized(this) {
                final T ret = ref;
                ref = newVal;
                notify();
                return ret;
            }
        }

        public T getNonNullAndSetToNull(final AtomicBoolean stopCondition) {
            synchronized(this) {
                // handle spurious wakes
                while(ref == null && !stopCondition.get()) {
                    try {
                        wait();
                    } catch(final InterruptedException ie) {
                        final T ret = ref;
                        ref = null;
                        return ret;
                    }
                }
                final T ret = ref;
                ref = null;
                return ret;
            }
        }
    }

    private static class StreamWatcher extends ProxyFilter {
        private final PseudoAtomicReference<VideoFrame> to = new PseudoAtomicReference<>(null);
        private final AtomicLong sequence = new AtomicLong(0);

        private void dispose(final VideoFrame toDispose) {
            if(toDispose != null)
                toDispose.close();
        }

        private Runnable fromProcessor(final VideoFrameFilter processor) {
            return () -> {
                while(!stop.get()) {
                    final VideoFrame frame = to.getNonNullAndSetToNull(stop);
                    if(frame != null && !stop.get()) {
                        if(traceOn)
                            LOGGER.trace("FRAME: dequeued frame: {}", frame.frameNumber());
                        try(final QuietCloseable qc = () -> dispose(frame);) {
                            processor.accept(frame);
                        } catch(final Exception exc) {
                            if(LOGGER.isDebugEnabled())
                                LOGGER.info("StreamWatcher: User supplied processor threw exception: {}", exc.getLocalizedMessage(), exc);
                            else
                                LOGGER.info("StreamWatcher: User supplied processor threw exception: {} (to see stack trace enable debug logging for {})",
                                    exc.getLocalizedMessage(), BreakoutFilter.class.getName());
                        }
                    } // otherwise we're stopped.
                }
            };
        }

        private StreamWatcher(final VideoFrame mat, final int numThreads, final VideoFrameFilter processor) {
            if(traceOn)
                LOGGER.trace("FRAME: Initial frame: {}, {}", mat.frameNumber(), mat);
            super.initPool(mat);
            for(int i = 0; i < numThreads; i++)
                threads.add(chain(new Thread(fromProcessor(processor), "Stream Watcher " + sequence.getAndIncrement()), t -> t.setDaemon(true),
                    t -> t.start()));
        }

        @Override
        public void accept(final VideoFrame frame) {
            if(traceOn)
                LOGGER.trace("FRAME: queuing frame: {}", frame.frameNumber());
            dispose(to.getAndSet(frame.shallowCopy()));
        }
    }

    // =================================================
    // Signals.
    // =================================================

    /**
     * This callback is the main filter callback.
     */
    private final BreakoutAPIRaw.push_frame_callback actualFilter(final boolean needToSendBackResults) {

        // If it needToSendBackResults then it will be closed in the native code automatically.
        // when it needToSendBackResults (same as needing to write the data), the callback DOES
        // NOT own the frame. And therefore the close and doNativeDelete need to be disabled.
        return new BreakoutAPIRaw.push_frame_callback() {
            @Override
            public void push_frame(final long frame) {
                if(traceOn)
                    LOGGER.trace("frame pushed :{}", frame);
                try(
                    final VideoFrame mat = new VideoFrame(
                        frame, System.currentTimeMillis(), frameNumber.getAndIncrement(), true) {

                        // mats are closed automatically in the native code
                        // once the push_frame returns.
                        @Override
                        public void doNativeDelete() {}
                    };

                ) {
                    // we need to copy the list because any one of these filters can change it
                    final List<VideoFrameFilter> curFilters = new ArrayList<>(filterStack);
                    curFilters.forEach(f -> f.accept(mat));
                    return;
                } catch(final VideoFrameFilterException vffe) {
                    LOGGER.error("Unexpected processing exception: {}", vffe.flowReturn, vffe);
                    return;
                } catch(final Exception e) {
                    LOGGER.error("Unexpected processing exception:", e);
                    return;
                }
            }
        };
    }
    // =================================================

    private synchronized void swap(final VideoFrameFilter currentFilter, final VideoFrameFilter newFilter) {
        final int index = IntStream.range(0, filterStack.size())
            .filter(i -> filterStack.get(i) == currentFilter)
            .findAny()
            .orElseThrow(() -> new VideoFrameFilterException(FlowReturn.ERROR));
        filterStack.set(index, newFilter);
    }

    private BreakoutFilter doDelayedConnect(final Function<VideoFrame, VideoFrameFilter> watcherSupplier, final boolean needToBeAbleToWriteData) {
        doConnect(new VideoFrameFilter() {
            @Override
            public void accept(final VideoFrame mac) {
                swap(this, watcherSupplier.apply(mac));
            }
        }, needToBeAbleToWriteData);
        return this;
    }

    private synchronized BreakoutFilter doConnect(final VideoFrameFilter listener, final boolean needsToWriteResults) {
        if(filterStack.size() == 0) {
            actualFilter = actualFilter(needsToWriteResults);
            BreakoutAPIRaw.set_push_frame_callback(me, actualFilter, needsToWriteResults ? 1 : 0);
        }
        filterStack.add(listener);
        return this;
    }
}
