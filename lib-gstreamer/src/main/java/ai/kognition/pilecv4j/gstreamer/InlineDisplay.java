package ai.kognition.pilecv4j.gstreamer;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.gstreamer.BreakoutFilter.CvMatAndCaps;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.KeyPressCallback;

import net.dempsy.util.MutableRef;

public class InlineDisplay implements Consumer<CvMatAndCaps> {
   private static final Logger LOGGER = LoggerFactory.getLogger(InlineDisplay.class);

   public static String DEFAULT_WINDOW_NAME = "inline-display";
   public static boolean DEFAULT_DEEP_COPY = true;

   private final boolean deepCopy;
   private ImageDisplay window = null;
   private Pipeline stopOnClose = null;
   private CountDownLatch stopLatch = null;
   private final Size screenDim;

   private final MutableRef<Size> adjustedSize = new MutableRef<>();

   public InlineDisplay(final ImageDisplay display) {
      this(display, DEFAULT_DEEP_COPY);
   }

   public InlineDisplay(final ImageDisplay display, final boolean deepCopy) {
      this.window = display;
      screenDim = null;
      this.deepCopy = deepCopy;
      this.window.setCloseCallback(() -> {
         if(stopOnClose != null) {
            GstUtils.stopBinOnEOS(stopOnClose, stopLatch);
            LOGGER.debug("Emitting EOS");
            stopOnClose.sendEvent(new EOSEvent());
         }
      });
   }

   private InlineDisplay(final Size screenDim, final boolean deepCopy, final KeyPressCallback kpc, final boolean preserveAspectRatio,
         final ImageDisplay.Implementation impl, final String windowName) {
      this.deepCopy = deepCopy;
      this.screenDim = screenDim;

      if(!preserveAspectRatio)
         adjustedSize.ref = screenDim;

      window = new ImageDisplay.Builder()
            .windowName(windowName)
            .implementation(impl)
            .closeCallback(() -> {
               if(stopOnClose != null) {
                  GstUtils.stopBinOnEOS(stopOnClose, stopLatch);
                  LOGGER.debug("Emitting EOS");
                  stopOnClose.sendEvent(new EOSEvent());
               }
            })
            .keyPressHandler(kpc)
            .build();

   }

   public static class Builder {

      private boolean deepCopy = DEFAULT_DEEP_COPY;
      private KeyPressCallback kpc = null;
      private Size screenDim = null;
      private boolean preserveAspectRatio = true;
      private ImageDisplay.Implementation impl = ImageDisplay.DEFAULT_IMPLEMENTATION;
      private String windowName = DEFAULT_WINDOW_NAME;

      public Builder deepCopy(final boolean deepCopy) {
         this.deepCopy = deepCopy;
         return this;
      }

      public Builder keyPressCallback(final KeyPressCallback kpc) {
         this.kpc = kpc;
         return this;
      }

      public Builder dim(final Size screenDim) {
         this.screenDim = screenDim;
         return this;
      }

      public Builder preserveAspectRatio(final boolean preserveAspectRatio) {
         this.preserveAspectRatio = preserveAspectRatio;
         return this;
      }

      public Builder implementation(final ImageDisplay.Implementation impl) {
         this.impl = impl;
         return this;
      }

      public Builder windowName(final String windowName) {
         this.windowName = windowName;
         return this;
      }

      public InlineDisplay build() {
         return new InlineDisplay(screenDim, deepCopy, kpc, preserveAspectRatio, impl, windowName);
      }
   }

   @Override
   public void accept(final CvMatAndCaps rac) {
      final CvMat m = rac.mat;

      if(screenDim != null) {
         if(adjustedSize.ref == null) {
            adjustedSize.ref = Utils.preserveAspectRatio(m, screenDim);
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
   }

   public void stopOnClose(final Pipeline pipe, final CountDownLatch stopLatch) {
      this.stopOnClose = pipe;
      this.stopLatch = stopLatch;
   }

   private void process(final CvMat lmat) {
      window.update(lmat);
   }
}
