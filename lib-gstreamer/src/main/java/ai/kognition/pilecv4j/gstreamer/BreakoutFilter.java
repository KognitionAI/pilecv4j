package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.elements.BaseTransform;
import org.freedesktop.gstreamer.lowlevel.GstAPI.GstCallback;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.gstreamer.guard.BufferWrap;
import ai.kognition.pilecv4j.image.CvMat;

import net.dempsy.util.Functional;

public class BreakoutFilter extends BaseTransform {
   private static Logger LOGGER = LoggerFactory.getLogger(BreakoutFilter.class);

   public static final String GST_NAME = "breakout";
   public static final String GTYPE_NAME = "GstBreakout";

   private static final AtomicBoolean inited = new AtomicBoolean(false);

   private ProxyFilter proxyFilter = null;

   private static final BreakoutAPI FILTER_API = BreakoutAPI.FILTER_API;

   public static void init() {
      if(!inited.getAndSet(true)) {
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
      public final CvMat mat;
      private boolean rasterDisowned = false;

      private CvMatAndCaps(final CvMat frameData, final Caps caps, final int w, final int h) {
         super(caps, w, h);
         this.mat = frameData;
      }

      /**
       * Create a BufferAndCaps using a separate ByteBuffer to store the frame data. The
       * ByteBuffer that ends up as {@code buffer} will not be the ByteBuffer associated
       * with {@code frameBuffer} but will contain a copy of the data.
       * 
       * This is primarily used to pool ByteBuffers that will outlive the scope of the call
       * back in the SlowFilterSlippage.
       */
      private CvMatAndCaps(final Buffer frameBuffer, final CvMat tmp, final Caps caps, final int w, final int h, final int type) {
         super(caps, w, h);
         try (BufferWrap bw = new BufferWrap(frameBuffer, false);) {
            final ByteBuffer bb = bw.map(false);
            final int capacity = bb.remaining();

            if(tmp != null && tmp.numBytes() == capacity)
               mat = tmp;
            else
               mat = new CvMat(h, w, type);

            mat.rasterAp(r -> {
               final ByteBuffer buffer = r.underlying();

               // copy the data
               buffer.rewind();
               buffer.put(bb);
            });
         }
      }

      @Override
      public void close() {
         if(mat != null && !rasterDisowned)
            mat.close();

         super.close();
      }

      @Override
      public String toString() {
         return "CvRasterAndCaps [raster=" + mat + ", rasterDisowned=" + rasterDisowned + ", caps=" + caps + ", width=" + width
               + ", height=" + height + "]";
      }

      private CvMat disownRaster() {
         rasterDisowned = true;
         return mat;
      }
   }

   public BreakoutFilter connect(final Consumer<CvMatAndCaps> filter) {
      return connect((Function<CvMatAndCaps, FlowReturn>)rac -> {
         filter.accept(rac);
         return FlowReturn.OK;
      });
   }

   public BreakoutFilter connect(final Function<CvMatAndCaps, FlowReturn> filter) {
      return connect((NEW_SAMPLE)elem -> {
         final int h = elem.getCurrentFrameHeight();
         final int w = elem.getCurrentFrameWidth();
         try (BufferWrap buffer = elem.getCurrentBuffer();
               CvMat raster = buffer.mapToCvMat(h, w, CvType.CV_8UC3, true);
               CvMatAndCaps bac = new CvMatAndCaps(raster, elem.getCurrentCaps(), w, h)) {
            return (raster == null) ? FlowReturn.OK : filter.apply(bac);
         }
      });
   }

   public BreakoutFilter connectSlowFilter(final Consumer<CvMatAndCaps> filter) {
      return connectSlowFilter(1, filter);
   }

   public BreakoutFilter connectSlowFilter(final int numThreads, final Consumer<CvMatAndCaps> filter) {
      final BreakoutFilter ret = connect(proxyFilter = new SlowFilterSlippage(numThreads, filter));
      return ret;
   }

   public BreakoutFilter connectStreamWatcher(final int numThreads, final Supplier<Consumer<CvMatAndCaps>> watcherSupplier) {
      final BreakoutFilter ret = connect(proxyFilter = new StreamWatcher(numThreads, watcherSupplier));
      return ret;
   }

   public BreakoutFilter connectDelayedStreamWatcher(final int numThreads,
         final Function<CvMatAndCaps, Supplier<Consumer<CvMatAndCaps>>> watcherSupplier) {
      connect(new NEW_SAMPLE() {

         @Override
         public FlowReturn new_sample(final BreakoutFilter elem) {
            final int h = elem.getCurrentFrameHeight();
            final int w = elem.getCurrentFrameWidth();
            try (final BufferWrap buffer = elem.getCurrentBuffer();
                  final CvMat raster = buffer.mapToCvMat(h, w, CvType.CV_8UC3, true);
                  final CvMatAndCaps bac = new CvMatAndCaps(raster, elem.getCurrentCaps(), w, h)) {

               disconnect(this);
               connectStreamWatcher(numThreads, watcherSupplier.apply(bac));

               return FlowReturn.OK;
            }
         }
      });
      return this;
   }

   @Override
   public void dispose() {
      if(proxyFilter != null) {
         proxyFilter.stop();
         try {
            Functional.<InterruptedException>recheck(() -> proxyFilter.threads.forEach(t -> uncheck(() -> t.join(1000))));
         } catch(final InterruptedException ie) {
            LOGGER.info("Interrupted while waiting for slow filter slippage thread for " + this.getName() + " to exit.");
         }

         proxyFilter.threads.forEach(t -> {
            if(t.isAlive())
               LOGGER.warn("The slow filter slippage thread for " + this.getName() + " never exited. Moving on.");
         });
      }

      super.dispose();
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

   public BreakoutFilter disconnect(final NEW_SAMPLE listener) {
      disconnect(NEW_SAMPLE.class, listener);
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

   private static abstract class ProxyFilter implements NEW_SAMPLE {
      protected final List<Thread> threads = new ArrayList<>();
      protected final AtomicBoolean stop = new AtomicBoolean(false);

      public void stop() {
         stop.set(true);
      }

   }

   private static class SlowFilterSlippage extends ProxyFilter {
      private final AtomicReference<CvMatAndCaps> to = new AtomicReference<>(null);
      private final AtomicReference<CvMatAndCaps> result = new AtomicReference<>(null);
      private CvMatAndCaps current = null;
      private boolean firstOne = true;

      private final AtomicReference<CvMat> storedBuffer = new AtomicReference<>();
      private static AtomicLong threadSequence = new AtomicLong(0);

      private void dispose(final CvMatAndCaps bac) {
         if(bac != null) {
            final CvMat old = storedBuffer.getAndSet(bac.disownRaster());
            if(old != null) {
               old.close();
               LOGGER.warn("Throwing away a ByteBuffer");
            }
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
                  processor.accept(frame);
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
      public FlowReturn new_sample(final BreakoutFilter elem) {
         try (final BufferWrap buffer = elem.getCurrentBuffer();) {

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
                     new CvMatAndCaps(buffer.obj, storedBuffer.getAndSet(null), elem.getCurrentCaps(), elem.getCurrentFrameWidth(),
                           elem.getCurrentFrameHeight(), CvType.CV_8UC3)));

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

               current.mat.rasterAp(r -> {
                  final ByteBuffer bb = buffer.map(true);
                  final ByteBuffer curBB = r.underlying();
                  curBB.rewind();
                  bb.put(curBB);
                  // cur stays current until it's replaced, then it's disposed.
               });
            }
         }
         return FlowReturn.OK;
      }
   }

   private static class StreamWatcher extends ProxyFilter {
      private final AtomicReference<CvMatAndCaps> to = new AtomicReference<>(null);

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
                  processor.accept(frame);
                  dispose(frame);
               } // otherwise we're stopped.
            }
         };
      }

      public StreamWatcher(final int numThreads, final Supplier<Consumer<CvMatAndCaps>> processorSupplier) {
         for(int i = 0; i < numThreads; i++)
            threads.add(chain(new Thread(fromProcessor(processorSupplier.get())), t -> t.setDaemon(true), t -> t.start()));
      }

      @Override
      public FlowReturn new_sample(final BreakoutFilter elem) {
         try (final BufferWrap buffer = elem.getCurrentBuffer();) {

            dispose(to.getAndSet(
                  new CvMatAndCaps(buffer.obj, null, elem.getCurrentCaps(), elem.getCurrentFrameWidth(),
                        elem.getCurrentFrameHeight(), CvType.CV_8UC3)));
         }
         return FlowReturn.OK;
      }

   }

}
