package ai.kognition.pilecv4j.image;

import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8U;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import net.dempsy.util.QuietCloseable;

public class SwtImageDisplay implements ImageDisplay {

   private final KeyPressCallback callback;
   private final Runnable closeCallback;
   private final String name;

   private final AtomicReference<Image> currentImageRef = new AtomicReference<Image>(null);
   private Display display;
   private Shell shell;
   private Canvas canvas;
   private final CountDownLatch eventLoopDoneLatch = new CountDownLatch(1);
   private final AtomicBoolean done = new AtomicBoolean(false);
   private Thread eventThread = null;
   private final CountDownLatch started = new CountDownLatch(1);

   private static class Disposable<T> implements QuietCloseable {
      private final T it;
      private final QuietCloseable ac;

      public Disposable(final T it, final QuietCloseable ac) {
         this.it = it;
         this.ac = ac;
      }

      public T get() {
         return it;
      }

      @Override
      public void close() {
         ac.close();
      }
   }

   private static Disposable<GC> disposable(final GC obj) {
      return new Disposable<GC>(obj, () -> obj.dispose());
   }

   public SwtImageDisplay(final Mat mat, final String name, final Runnable closeCallback, final KeyPressCallback kpCallback) {
      this.closeCallback = closeCallback;
      this.name = name;
      // create a callback that ignores the keypress but polls the state of the closeNow
      this.callback = kpCallback;
      setup(mat);
   }

   public void waitForPaint() throws InterruptedException {
      started.await();
   }

   private SwtImageDisplay(final boolean dontInitMe) {
      this.name = null;
      this.closeCallback = null;
      this.callback = null;
      shell = new Shell(display);
      shell.setLayout(new FillLayout());
   }

   private void setup(final Mat mat) {
      final CountDownLatch latch = new CountDownLatch(1);

      eventThread = new Thread(() -> {
         try {
            display = new Display();
            shell = new Shell(display);
            if(name != null) shell.setText(name);
            shell.setLayout(new FillLayout());

            Image currentImage = null;

            if(mat != null && mat.cols() > 0 && mat.rows() > 0) {
               currentImage = new Image(display, convertToSWT(mat));
            }

            if(currentImage == null) {
               final int width = 150, height = 200;
               currentImage = new Image(display, width, height);
               try (Disposable<GC> disposeable = disposable(new GC(currentImage))) {
                  final GC gc = disposeable.get();
                  gc.fillRectangle(0, 0, width, height);
                  gc.drawLine(0, 0, width, height);
                  gc.drawLine(0, height, width, 0);
                  gc.drawText("Default Image", 10, 10);
               }
            }

            shell.setBounds(currentImage.getBounds());
            currentImageRef.set(currentImage);

            final Point origin = new Point(0, 0);
            canvas = new Canvas(shell, SWT.NO_BACKGROUND | SWT.NO_REDRAW_RESIZE | SWT.V_SCROLL | SWT.H_SCROLL);

            final ScrollBar hBar = canvas.getHorizontalBar();
            hBar.addListener(SWT.Selection, new Listener() {
               @Override
               public void handleEvent(final Event e) {
                  final Image currentImage = currentImageRef.get();
                  if(currentImage != null) {
                     final int hSelection = hBar.getSelection();
                     final int destX = -hSelection - origin.x;
                     final Rectangle rect = currentImage.getBounds();
                     canvas.scroll(destX, 0, 0, 0, rect.width, rect.height, false);
                     origin.x = -hSelection;
                  }
               }
            });
            final ScrollBar vBar = canvas.getVerticalBar();
            vBar.addListener(SWT.Selection, new Listener() {
               @Override
               public void handleEvent(final Event e) {
                  final Image currentImage = currentImageRef.get();
                  if(currentImage != null) {
                     final int vSelection = vBar.getSelection();
                     final int destY = -vSelection - origin.y;
                     final Rectangle rect = currentImage.getBounds();
                     canvas.scroll(0, destY, 0, 0, rect.width, rect.height, false);
                     origin.y = -vSelection;
                  }
               }
            });
            canvas.addListener(SWT.Resize, new Listener() {
               @Override
               public void handleEvent(final Event e) {
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
               }
            });
            canvas.addListener(SWT.Paint, new Listener() {
               @Override
               public void handleEvent(final Event e) {
                  final Image currentImage = currentImageRef.get();
                  if(currentImage != null) {
                     final GC gc = e.gc;
                     gc.drawImage(currentImage, origin.x, origin.y);
                     final Rectangle rect = currentImage.getBounds();
                     final Rectangle client = canvas.getClientArea();
                     final int marginWidth = client.width - rect.width;
                     if(marginWidth > 0) {
                        gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                     }
                     final int marginHeight = client.height - rect.height;
                     if(marginHeight > 0) {
                        gc.fillRectangle(0, rect.height, client.width, marginHeight);
                     }
                  }
                  started.countDown();
               }
            });

            if(callback != null) {
               canvas.addKeyListener(new KeyListener() {

                  @Override
                  public void keyReleased(final KeyEvent e) {}

                  @Override
                  public void keyPressed(final KeyEvent e) {
                     callback.keyPressed(e.keyCode);
                  }
               });
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
      try {
         latch.await();
      } catch(final InterruptedException ie) {

      }
   }

   @Override
   public synchronized void update(final Mat image) {
      Display.getDefault().syncExec(() -> {
         final ImageData next = convertToSWT(image);
         final Image prev = currentImageRef.getAndSet(new Image(display, next));
         if(prev != null)
            prev.dispose();
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
            id.setup(iioimage);
         } else
            id.setup(null);

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
