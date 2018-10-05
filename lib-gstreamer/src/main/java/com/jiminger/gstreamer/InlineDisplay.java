package com.jiminger.gstreamer;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BreakoutFilter.CvRasterAndCaps;
import com.jiminger.gstreamer.util.GstUtils;
import com.jiminger.image.CvMat;
import com.jiminger.image.CvRaster;
import com.jiminger.image.ImageDisplay;
import com.jiminger.image.ImageDisplay.KeyPressCallback;
import com.jiminger.image.ImageDisplay.WindowHandle;

import net.dempsy.util.MutableRef;

public class InlineDisplay implements Consumer<CvRasterAndCaps> {
   private static final Logger LOGGER = LoggerFactory.getLogger(InlineDisplay.class);

   private final boolean deepCopy;
   private WindowHandle window = null;
   private Pipeline stopOnClose = null;
   private CountDownLatch stopLatch = null;
   private final KeyPressCallback kpc;
   private final Size screenDim;

   private final MutableRef<Size> adjustedSize = new MutableRef<>();

   private InlineDisplay(final Size screenDim, final boolean deepCopy, final KeyPressCallback kpc, final boolean preserveAspectRatio) {
      this.deepCopy = deepCopy;
      this.kpc = kpc;
      this.screenDim = screenDim;

      if(!preserveAspectRatio)
         adjustedSize.ref = screenDim;
   }

   public InlineDisplay(final boolean deepCopy, final KeyPressCallback kpc) {
      this(null, deepCopy, kpc, true);
   }

   public InlineDisplay(final boolean deepCopy) {
      this(deepCopy, null);
   }

   public InlineDisplay() {
      this(true, null);
   }

   public InlineDisplay(final Size screenDim, final KeyPressCallback kpc) {
      this(screenDim, true, kpc, true);
   }

   public InlineDisplay(final Size screenDim, final boolean preserveAspectRatio) {
      this(screenDim, true, null, preserveAspectRatio);
   }

   public InlineDisplay(final Size screenDim, final boolean preserveAspectRatio, final KeyPressCallback kpc) {
      this(screenDim, true, kpc, preserveAspectRatio);
   }

   public InlineDisplay(final Size screenDim) {
      this(screenDim, null);
   }

   @Override
   public void accept(final CvRasterAndCaps rac) {
      final CvRaster r = rac.raster;

      r.matAp(m -> {
         if(screenDim != null) {
            if(adjustedSize.ref == null) {
               // calculate the appropriate resize
               final double fh = screenDim.height / m.rows();
               final double fw = screenDim.width / m.cols();
               final double scale = fw < fh ? fw : fh;
               if(scale >= 1.0)
                  adjustedSize.ref = new Size(m.width(), m.height());
               else
                  adjustedSize.ref = new Size(Math.round(m.width() * scale), Math.round(m.height() * scale));
               System.out.println(adjustedSize.ref);
            }

            try (final CvMat lmat = new CvMat()) {
               Imgproc.resize(m, lmat, adjustedSize.ref, -1, -1, Imgproc.INTER_NEAREST);
               process(lmat);
            }
         } else {
            try (final CvMat lmat = deepCopy ? CvMat.deepCopy(m) : CvMat.shallowCopy(m)) {
               process(lmat);
            }
         }
      });
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
