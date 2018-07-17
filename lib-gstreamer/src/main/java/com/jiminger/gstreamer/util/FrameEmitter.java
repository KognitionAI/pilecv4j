package com.jiminger.gstreamer.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BinManager;
import com.jiminger.gstreamer.BreakoutFilter;
import com.jiminger.gstreamer.BreakoutFilter.CvRasterAndCaps;
import com.jiminger.gstreamer.CapsBuilder;
import com.jiminger.gstreamer.guard.ElementWrap;

/**
 * This class can be used to source a fixed number of frames for testing purposes.
 */
public class FrameEmitter implements AutoCloseable {
   public static boolean HACK_FRAME = true;
   private final static Logger LOGGER = LoggerFactory.getLogger(FrameEmitter.class);
   private static AtomicInteger sequence = new AtomicInteger(0);
   private ElementWrap<Bin> element;
   public final int numFrames;

   private int curFrames = 0;
   private BreakoutFilter breakout = null;
   public boolean emitted = false;

   public FrameEmitter(final String sourceUri, final int numFrames) {
      final int seq = sequence.getAndIncrement();
      breakout = new BreakoutFilter("emitter" + seq)
            .connect((final CvRasterAndCaps bac) -> {
               if(HACK_FRAME)
                  bac.raster.underlying().put(0, (byte)curFrames);
               if(isDone()) {
                  if(!emitted) {
                     breakout.sendEvent(new EOSEvent());
                     emitted = true;
                     return FlowReturn.UNEXPECTED;
                  }
               } else {
                  LOGGER.trace("emitter emitting frame {}", curFrames);
                  curFrames++;
               }
               return FlowReturn.OK;
            });

      element = new ElementWrap<>(new BinManager()
            .delayed(new URIDecodeBin("source")).with("uri", sourceUri)
            .make("videoconvert")
            .caps(new CapsBuilder("video/x-raw")
                  .addFormatConsideringEndian()
                  .buildString())
            .add(breakout)
            .buildBin());

      this.numFrames = numFrames;
   }

   public boolean isDone() {
      return(curFrames >= numFrames);
   }

   public Bin disown() {
      final Bin ret = element.disown();
      element = null;
      return ret;
   }

   @Override
   public void close() throws Exception {
      if(element != null)
         element.close();
   }

}
