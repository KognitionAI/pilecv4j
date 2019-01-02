package ai.kognition.pilecv4j.image.display.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.Mat;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

import net.dempsy.util.Functional;

public class ScrollableSwtCanvasImageDisplay extends SwtCanvasImageDisplay {

   private final Point origin = new Point(0, 0);

   public ScrollableSwtCanvasImageDisplay(final Shell parent, final Runnable closeCallback, final KeyPressCallback kpCallback,
         final SelectCallback selectCallback) {

      super.setup(new Canvas(parent, SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE | SWT.V_SCROLL | SWT.H_SCROLL), closeCallback, kpCallback, selectCallback);

      final Display display = SwtUtils.getDisplay();
      ImageDisplay.syncExec(() -> {

         canvas.addListener(SWT.Paint, e -> {
            try (final CvMat lcurrentImageMat = Functional.applyIfExistsAndReturnResult(currentImageRef, CvMat::shallowCopy);) {
               if(lcurrentImageMat != null) {
                  final Image lcurrentImage = new Image(display, convertToDisplayableSWT(lcurrentImageMat));
                  // Draw the image into the current graphics context at the current position
                  final GC gc = e.gc;
                  gc.drawImage(lcurrentImage, this.origin.x, this.origin.y);

                  // get the bounds of the image.
                  final Rectangle rect = lcurrentImage.getBounds();

                  // get the bounds of the canvas
                  final Rectangle client = canvas.getClientArea();

                  // there may be a margin between the image and the edge of the canvas
                  // if the canvas is bigger than the image.
                  final int marginWidth = client.width - rect.width;
                  if(marginWidth > 0) {
                     gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                  }
                  final int marginHeight = client.height - rect.height;
                  if(marginHeight > 0) {
                     gc.fillRectangle(0, rect.height, client.width, marginHeight);
                  }

                  // if we haven't packed the display layout since we either never have
                  // or the image changed size, we need to do that now.
                  if(!alreadySized.get()) {
                     final GridData gridData = new GridData(GridData.FILL_BOTH);

                     gridData.widthHint = lcurrentImage.getBounds().width;
                     gridData.heightHint = lcurrentImage.getBounds().height;

                     canvas.setLayoutData(gridData);
                     canvas.layout(true, true);
                     parent.layout(true, true);
                     parent.pack(true);
                     alreadySized.set(true);
                  }
               }
            }
         });

         final ScrollBar hBar = canvas.getHorizontalBar();
         hBar.addListener(SWT.Selection, e -> {
            try (final CvMat currentImage = Functional.applyIfExistsAndReturnResult(currentImageRef, CvMat::shallowCopy);) {
               if(currentImage != null) {
                  final int hSelection = hBar.getSelection();
                  final int destX = -hSelection - origin.x;
                  final Rectangle rect = new Rectangle(0, 0, currentImage.width(), currentImage.height());
                  canvas.scroll(destX, 0, 0, 0, rect.width, rect.height, false);
                  origin.x = -hSelection;
               }
            }
         });

         final ScrollBar vBar = canvas.getVerticalBar();
         vBar.addListener(SWT.Selection, e -> {
            try (final CvMat currentImage = Functional.applyIfExistsAndReturnResult(currentImageRef, CvMat::shallowCopy);) {
               if(currentImage != null) {
                  final int vSelection = vBar.getSelection();
                  final int destY = -vSelection - origin.y;
                  final Rectangle rect = new Rectangle(0, 0, currentImage.width(), currentImage.height());
                  canvas.scroll(0, destY, 0, 0, rect.width, rect.height, false);
                  origin.y = -vSelection;
               }
            }
         });

         canvas.addListener(SWT.Resize, e -> {
            try (final CvMat currentImage = Functional.applyIfExistsAndReturnResult(currentImageRef, CvMat::shallowCopy);) {
               if(currentImage != null) {
                  final Rectangle rect = new Rectangle(0, 0, currentImage.width(), currentImage.height());
                  final Rectangle client = canvas.getClientArea();
                  hBar.setMaximum(rect.width);
                  vBar.setMaximum(rect.height);
                  hBar.setThumb(Math.min(rect.width, client.width));
                  vBar.setThumb(Math.min(rect.height, client.height));
                  final int hPage = rect.width - client.width;
                  final int vPage = rect.height - client.height;
                  int hSelection = hBar.getSelection();
                  int vSelection = vBar.getSelection();
                  if(hSelection >= hPage) {
                     if(hPage <= 0)
                        hSelection = 0;
                     origin.x = -hSelection;
                  }
                  if(vSelection >= vPage) {
                     if(vPage <= 0)
                        vSelection = 0;
                     origin.y = -vSelection;
                  }
                  canvas.redraw();
               }
            }
         });
      });
   }

   @Override
   public org.opencv.core.Point canvasLocationToImageLocation(final int x, final int y) {
      return new org.opencv.core.Point(x - origin.x, y - origin.y);
   }

   public ImageData convertToDisplayableSWT(final Mat image) {
      return SwtUtils.convertToDisplayableSWT(image);
   }
}
