package ai.kognition.pilecv4j.image;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

public interface ImageDisplay extends QuietCloseable {
   public static final String DEFAULT_WINDOWS_NAME_PREFIX="Window";
   static AtomicLong sequence = new AtomicLong(0);

   static final Logger LOGGER = LoggerFactory.getLogger(ImageDisplay.class);

   public void update(final Mat toUpdate);
   
   public void waitUntilClosed() throws InterruptedException;

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
      private String windowName = DEFAULT_WINDOWS_NAME_PREFIX + " " + sequence.incrementAndGet();
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
   
   public static CvMat displayable(Mat mat) {
	   final int type = mat.type();
	   
	   final int depth = CvType.depth(type);
	   if (depth == CvType.CV_8U || depth == CvType.CV_8S)
		   return CvMat.shallowCopy(mat);
	   
	   final int inChannels = mat.channels();
	   if (inChannels != 1 && inChannels != 3)
		   throw new IllegalArgumentException("Cannot handle an image of type " + CvType.typeToString(type) + "  yet.");
	   
	   int newType = (inChannels == 1 ) ? CvType.CV_8UC1 : CvType.CV_8UC3;
	   try (CvMat ret = new CvMat()) {

		   mat.convertTo(ret, newType, 1.0 / 256.0);
		   return ret.returnMe();
	   }
   }

   public static void main(final String[] args) throws Exception {
	      try (final ImageDisplay id = new Builder()
	    		  .implementation(Implementation.SWT)
	    		  .build();) {
	    	 String string = (args.length > 0 ? args[0] : null);
	    	 if (string == null) {
	    		 Display display = new Display();
	    		 Shell shell = new Shell(display);
	    		 try (QuietCloseable c1 = () -> display.close();
	    				 QuietCloseable c2 = () -> shell.close()){
	    			 final FileDialog dialog = new FileDialog(shell, SWT.OPEN);
	    			 dialog.setText("Open an image file or cancel");
	    			 string = dialog.open();
	    		 }
	    	 }
	    	 

	         if(string != null) {
	            final CvMat iioimage = ImageFile.readMatFromFile(string);
	            id.update(iioimage);
	         } 

	         id.waitUntilClosed();
	      }
	   }


}
