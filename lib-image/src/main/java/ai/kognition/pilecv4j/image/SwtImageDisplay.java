package ai.kognition.pilecv4j.image;

import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8U;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import net.dempsy.util.Functional;

public class SwtImageDisplay implements ImageDisplay {

   private final KeyPressCallback callback;
   private final Runnable closeCallback;
   private final SelectCallback selectCallback;
   private final String name;

   private final AtomicReference<Image> currentImageRef = new AtomicReference<Image>(null);
   private Display display;
   private Shell shell;
   private Canvas canvas;
   private final CountDownLatch eventLoopDoneLatch = new CountDownLatch(1);
   private final AtomicBoolean done = new AtomicBoolean(false);
   private Thread eventThread = null;
   private final CountDownLatch started = new CountDownLatch(1);

   final AtomicBoolean alreadySized = new AtomicBoolean(false);

   public SwtImageDisplay(final Mat mat, final String name, final Runnable closeCallback, final KeyPressCallback kpCallback,
         final SelectCallback selectCallback) {
      this.closeCallback = closeCallback;
      this.name = name;
      // create a callback that ignores the keypress but polls the state of the closeNow
      this.callback = kpCallback;
      this.selectCallback = selectCallback;
      setup();
      if(mat != null)
         update(mat);
   }

   public void waitForPaint() throws InterruptedException {
      started.await();
   }

   private SwtImageDisplay(final boolean dontInitMe) {
      this.name = null;
      this.closeCallback = null;
      this.callback = null;
      this.selectCallback = null;
      shell = new Shell(display);
      shell.setLayout(new FillLayout());
   }

   private void setup() {
      final CountDownLatch latch = new CountDownLatch(1);

      eventThread = new Thread(() -> {
         try {
            display = new Display();
            shell = new Shell(display);
            if(name != null) shell.setText(name);

            final GridLayout layout = new GridLayout();
            layout.numColumns = 1;

            shell.setLayout(layout);

            final Point origin = new Point(0, 0);
            canvas = new Canvas(shell, SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE | SWT.V_SCROLL | SWT.H_SCROLL);
            final ScrollBar hBar = canvas.getHorizontalBar();
            hBar.addListener(SWT.Selection, e -> {
               final Image currentImage = currentImageRef.get();
               if(currentImage != null) {
                  final int hSelection = hBar.getSelection();
                  final int destX = -hSelection - origin.x;
                  final Rectangle rect = currentImage.getBounds();
                  canvas.scroll(destX, 0, 0, 0, rect.width, rect.height, false);
                  origin.x = -hSelection;
               }
            });
            final ScrollBar vBar = canvas.getVerticalBar();
            vBar.addListener(SWT.Selection, e -> {
               final Image currentImage = currentImageRef.get();
               if(currentImage != null) {
                  final int vSelection = vBar.getSelection();
                  final int destY = -vSelection - origin.y;
                  final Rectangle rect = currentImage.getBounds();
                  canvas.scroll(0, destY, 0, 0, rect.width, rect.height, false);
                  origin.y = -vSelection;
               }
            });
            canvas.addListener(SWT.Resize, e -> {
               final Image currentImage = currentImageRef.get();
               if(currentImage != null) {
                  final Rectangle rect = currentImage.getBounds();
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
            });

            canvas.addListener(SWT.Paint, e -> {
               final Image lcurrentImage = currentImageRef.get();
               if(lcurrentImage != null) {
                  final GC gc = e.gc;
                  gc.drawImage(lcurrentImage, origin.x, origin.y);
                  final Rectangle rect = lcurrentImage.getBounds();
                  final Rectangle client = canvas.getClientArea();
                  final int marginWidth = client.width - rect.width;
                  if(marginWidth > 0) {
                     gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                  }
                  final int marginHeight = client.height - rect.height;
                  if(marginHeight > 0) {
                     gc.fillRectangle(0, rect.height, client.width, marginHeight);
                  }

                  if(!alreadySized.get()) {
                     final GridData gridData = new GridData(GridData.FILL_BOTH);

                     gridData.widthHint = lcurrentImage.getBounds().width;
                     gridData.heightHint = lcurrentImage.getBounds().height;

                     canvas.setLayoutData(gridData);
                     canvas.layout(true, true);
                     shell.layout(true, true);
                     shell.pack(true);
                     alreadySized.set(true);
                  }
               }
               started.countDown();
            });

            if(selectCallback != null) {
               canvas.addMouseListener(new MouseListener() {

                  @Override
                  public void mouseUp(final MouseEvent e) {}

                  @Override
                  public void mouseDown(final MouseEvent e) {
                     // origin is negative when scrolled since it's in that direction from the origin of the viewport.
                     if(selectCallback.select(new org.opencv.core.Point(e.x - origin.x, e.y - origin.y))) {
                        // need to close the shell
                        close();
                     }
                  }

                  @Override
                  public void mouseDoubleClick(final MouseEvent e) {}
               });
            }

            if(callback != null) {
               canvas.addKeyListener(new KeyListener() {

                  @Override
                  public void keyReleased(final KeyEvent e) {}

                  @Override
                  public void keyPressed(final KeyEvent e) {
                     if(callback.keyPressed(e.keyCode)) {
                        // need to close the shell
                        close();
                     }
                  }
               });
            }

            if(closeCallback != null) {
               canvas.addListener(SWT.Dispose, e -> closeCallback.run());
            }
            shell.open();
            latch.countDown();
            while(!shell.isDisposed() && !done.get()) {
               if(!display.readAndDispatch())
                  display.sleep();
            }
         } finally {
            if(shell != null) {
               if(!shell.isDisposed()) {
                  final Image prev = currentImageRef.getAndSet(null);
                  if(prev != null)
                     prev.dispose();
                  if(canvas != null)
                     canvas.dispose();
                  if(shell != null)
                     shell.dispose();
                  if(display != null)
                     display.dispose();
               }
               eventLoopDoneLatch.countDown();
            }
         }
      }, "SWT Event Loop");
      eventThread.start();
      Functional.ignore(() -> latch.await());
   }

   private Rectangle prevBounds = null;

   @Override
   public synchronized void update(final Mat image) {
      Display.getDefault().syncExec(() -> {
         final ImageData next = convertToSWT(image);
         final Image nextImage = new Image(display, next);
         final Image prev = currentImageRef.getAndSet(nextImage);
         if(prev != null)
            prev.dispose();
         final Rectangle bounds = nextImage.getBounds();
         if(!bounds.equals(prevBounds)) {
            // shell.setSize(bounds.width, bounds.height);
            alreadySized.set(false);
            prevBounds = bounds;
         }
         if(canvas != null)
            canvas.redraw();
      });
   }

   @Override
   public void close() {
      if(!done.get()) {
         done.set(true);
         Display.getDefault().syncExec(() -> {
            if(Thread.currentThread() == eventThread) {
               if(canvas != null) {
                  canvas.dispose();
                  final Image img = currentImageRef.getAndSet(null);
                  if(img != null)
                     img.dispose();
                  display.dispose();
               }
            }
         });
      }
   }

   public void waitUntilClosed() throws InterruptedException {
      eventLoopDoneLatch.await();
   }

   public static void main(final String[] args) throws Exception {
      try (final SwtImageDisplay id = new SwtImageDisplay(true);) {
         final FileDialog dialog = new FileDialog(id.shell, SWT.OPEN);
         dialog.setText("Open an image file or cancel");
         final String string = dialog.open();

         if(string != null) {
            final CvMat iioimage = ImageFile.readMatFromFile(string);
            id.setup();
            id.update(iioimage);
         } else
            id.setup();

         id.waitUntilClosed();
      }
   }

   @FunctionalInterface
   private static interface PixGet {
      int get(int row, int col);
   }

   static ImageData convertToSWT(final Mat mat) {
      final int inChannels = mat.channels();
      final int cvDepth = CvType.depth(mat.type());

      final int width = mat.cols();
      final int height = mat.rows();
      if(inChannels == 1 || inChannels == 3) {
         if(cvDepth != CV_8U && cvDepth != CV_8S)
            throw new IllegalArgumentException("Cannot convert BGR Mat to SWT image with elements larger than a byte yet.");

         final ImageData id = new ImageData(width, height, inChannels == 1 ? 8 : 24,
               inChannels == 1 ? new PaletteData(0x0000FF, 0x0000FF, 0x0000FF) : new PaletteData(0x0000FF, 0x00FF00, 0xFF0000));
         CvMat.rasterAp(mat, raster -> raster.currentBuffer.get(id.data));
         return id;
      } else if(inChannels == 4)
         throw new IllegalArgumentException("Can't handle alpha channel yet.");
      else
         throw new IllegalArgumentException("Can't handle an image with " + inChannels + " channels");
   }

}
