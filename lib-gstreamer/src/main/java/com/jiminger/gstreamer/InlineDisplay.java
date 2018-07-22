package com.jiminger.gstreamer;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BreakoutFilter.CvRasterAndCaps;
import com.jiminger.gstreamer.util.GstUtils;
import com.jiminger.image.CvMat;
import com.jiminger.image.CvRaster;
import com.jiminger.image.ImageDisplay;
import com.jiminger.image.ImageDisplay.KeyPressCallback;
import com.jiminger.image.ImageDisplay.WindowHandle;

public class InlineDisplay implements Consumer<CvRasterAndCaps> {
   private static final Logger LOGGER = LoggerFactory.getLogger(InlineDisplay.class);

   private final boolean deepCopy;
   private WindowHandle window = null;
   private Pipeline stopOnClose = null;
   private CountDownLatch stopLatch = null;
   private final KeyPressCallback kpc;

   public InlineDisplay(final boolean deepCopy, final KeyPressCallback kpc) {
      this.deepCopy = deepCopy;
      this.kpc = kpc;
   }

   public InlineDisplay(final boolean deepCopy) {
      this(deepCopy, null);
   }

   public InlineDisplay() {
      this(true, null);
   }

   @Override
   public void accept(final CvRasterAndCaps rac) {
      final CvRaster r = rac.raster;
      try (final CvMat lmat = r.matOp(m -> deepCopy ? CvMat.deepCopy(m) : CvMat.shallowCopy(m))) {
         process(lmat);
      }
   }

   public void stopOnClose(final Pipeline pipe, final CountDownLatch stopLatch) {
      this.stopOnClose = pipe;
      this.stopLatch = stopLatch;
   }

   private void process(final CvMat lmat) {
      if(window == null)
         window = ImageDisplay.show(lmat, "inline-display", () -> {
            if(stopOnClose != null) {
               GstUtils.stopBinOnEOS(stopOnClose, stopLatch);
               LOGGER.debug("Emitting EOS");
               stopOnClose.sendEvent(new EOSEvent());
            }
         }, kpc);
      else
         window.update(lmat);
   }
}
