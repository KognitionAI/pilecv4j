package ai.kognition.pilecv4j.image;

import org.opencv.core.Mat;

import net.dempsy.util.QuietCloseable;

public interface ImageDisplay extends QuietCloseable {

   public void update(final Mat toUpdate);

   @FunctionalInterface
   public static interface KeyPressCallback {
      public boolean keyPressed(int keyPressed);
   }

   public static enum Implementation {
      HIGHGUI, SWT
   }

   public static Implementation DEFAULT_IMPLEMENTATION = Implementation.HIGHGUI;

   public static class Builder {
      private KeyPressCallback keyPressHandler = null;
      private Implementation implementation = DEFAULT_IMPLEMENTATION;
      private Runnable closeCallback = null;
      private Mat toShow;
      private String windowName;

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
            case HIGHGUI:
               return new CvImageDisplay(toShow, windowName, closeCallback, keyPressHandler);
            case SWT:
               return new SwtImageDisplay(toShow, windowName, closeCallback, keyPressHandler);
            default:
               throw new IllegalStateException();
         }
      }
   }

}
