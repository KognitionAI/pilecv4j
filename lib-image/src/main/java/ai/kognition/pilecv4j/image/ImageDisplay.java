package ai.kognition.pilecv4j.image;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

public interface ImageDisplay extends QuietCloseable {
   static final Logger LOGGER = LoggerFactory.getLogger(ImageDisplay.class);

   public void update(final Mat toUpdate);

   @FunctionalInterface
   public static interface KeyPressCallback {
      public boolean keyPressed(int keyPressed);
   }

   @FunctionalInterface
   public static interface SelectCallback {
      public boolean select(Point pointClicked);
   }

   public static enum Implementation {
      HIGHGUI, SWT
   }

   public static Implementation DEFAULT_IMPLEMENTATION = Implementation.HIGHGUI;

   public static class Builder {
      private KeyPressCallback keyPressHandler = null;
      private Implementation implementation = DEFAULT_IMPLEMENTATION;
      private Runnable closeCallback = null;
      private Mat toShow = null;
      private String windowName = "";
      private SelectCallback selectCallback = null;

      public Builder keyPressHandler(final KeyPressCallback keyPressHandler) {
         this.keyPressHandler = keyPressHandler;
         return this;
      }

      public Builder implementation(final Implementation impl) {
         this.implementation = impl;
         return this;
      }

      public Builder closeCallback(final Runnable closeCallback) {
         this.closeCallback = closeCallback;
         return this;
      }

      public Builder selectCallback(final SelectCallback selectCallback) {
         this.selectCallback = selectCallback;
         return this;
      }

      public Builder windowName(final String windowName) {
         this.windowName = windowName;
         return this;
      }

      public Builder show(final Mat toShow) {
         this.toShow = toShow;
         return this;
      }

      public ImageDisplay build() {
         switch(implementation) {
            case HIGHGUI: {
               if(selectCallback != null)
                  LOGGER.info("The select callback will be ignored when using the HIGHGUI implementation of ImageDisplay");
               return new CvImageDisplay(toShow, windowName, closeCallback, keyPressHandler);
            }
            case SWT:
               return new SwtImageDisplay(toShow, windowName, closeCallback, keyPressHandler, selectCallback);
            default:
               throw new IllegalStateException();
         }
      }
   }

}
