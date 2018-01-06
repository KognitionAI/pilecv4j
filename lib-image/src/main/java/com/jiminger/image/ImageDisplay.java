package com.jiminger.image;

import static org.opencv.core.CvType.CV_16S;
import static org.opencv.core.CvType.CV_16U;
import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8U;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
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

import net.dempsy.util.library.NativeLibraryLoader;

public class ImageDisplay implements AutoCloseable {
    static {
        NativeLibraryLoader.init();
    }

    private final AtomicReference<Image> currentImageRef = new AtomicReference<Image>(null);
    private Display display;
    private Shell shell;
    private Canvas canvas;
    private final CountDownLatch eventLoopDoneLatch = new CountDownLatch(1);
    private final AtomicBoolean done = new AtomicBoolean(false);
    private Thread eventThread = null;
    private final CountDownLatch started = new CountDownLatch(1);

    private static interface AcNoThrow extends AutoCloseable {
        @Override
        public void close();
    }

    private static class Disposable<T> implements AcNoThrow {
        private final T it;
        private final AcNoThrow ac;

        public Disposable(final T it, final AcNoThrow ac) {
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

    public ImageDisplay() {
        this(null);
    }

    public ImageDisplay(final Mat mat) {
        setup(mat);
    }

    public void waitForPaint() throws InterruptedException {
        started.await();
    }

    private ImageDisplay(final boolean dontInitMe) {
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
    }

    private void setup(final Mat mat) {
        final CountDownLatch latch = new CountDownLatch(1);

        eventThread = new Thread(() -> {
            try {
                display = new Display();
                shell = new Shell(display);
                shell.setLayout(new FillLayout());

                Image currentImage = null;

                if (mat != null && mat.width() > 0 && mat.height() > 0) {
                    currentImage = new Image(display, convertToSWT(mat));
                }

                if (currentImage == null) {
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
                        if (currentImage != null) {
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
                        if (currentImage != null) {
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
                        if (currentImage != null) {
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
                            if (hSelection >= hPage) {
                                if (hPage <= 0)
                                    hSelection = 0;
                                origin.x = -hSelection;
                            }
                            if (vSelection >= vPage) {
                                if (vPage <= 0)
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
                        if (currentImage != null) {
                            final GC gc = e.gc;
                            gc.drawImage(currentImage, origin.x, origin.y);
                            final Rectangle rect = currentImage.getBounds();
                            final Rectangle client = canvas.getClientArea();
                            final int marginWidth = client.width - rect.width;
                            if (marginWidth > 0) {
                                gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                            }
                            final int marginHeight = client.height - rect.height;
                            if (marginHeight > 0) {
                                gc.fillRectangle(0, rect.height, client.width, marginHeight);
                            }
                        }
                        started.countDown();
                    }
                });
                shell.open();
                latch.countDown();
                while (!shell.isDisposed() && !done.get()) {
                    if (!display.readAndDispatch())
                        display.sleep();
                }
            } finally {
                if (!shell.isDisposed()) {
                    final Image prev = currentImageRef.getAndSet(null);
                    if (prev != null)
                        prev.dispose();
                    if (canvas != null)
                        canvas.dispose();
                    if (shell != null)
                        shell.dispose();
                    if (display != null)
                        display.dispose();
                }
                eventLoopDoneLatch.countDown();
            }
        }, "SWT Event Loop");
        eventThread.start();
        try {
            latch.await();
        } catch (final InterruptedException ie) {

        }
    }

    public void update(final Mat image) {
        Display.getDefault().syncExec(() -> {
            final ImageData next = convertToSWT(image);
            final Image prev = currentImageRef.getAndSet(new Image(display, next));
            if (prev != null)
                prev.dispose();
            if (canvas != null)
                canvas.redraw();
        });
    }

    @Override
    public void close() {
        if (!done.get()) {
            done.set(true);
            Display.getDefault().syncExec(() -> {
                if (Thread.currentThread() == eventThread) {
                    if (canvas != null) {
                        canvas.dispose();
                        final Image img = currentImageRef.getAndSet(null);
                        if (img != null)
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
        try (final ImageDisplay id = new ImageDisplay(true);) {
            final FileDialog dialog = new FileDialog(id.shell, SWT.OPEN);
            dialog.setText("Open an image file or cancel");
            final String string = dialog.open();

            if (string != null) {
                final Mat iioimage = ImageFile.readMatFromFile(string);
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

    static ImageData convertToSWT(final Mat in) {
        PaletteData pd;
        final int depth;
        PixGet getter = null;
        final int inChannels = in.channels();
        final byte[] tbbuf = new byte[1];
        final short[] tsbuf = new short[1];
        final int cvDepth = CvType.depth(in.type());

        final int width = in.width();
        final int height = in.height();
        if (inChannels == 1) { // assume gray
            final CvRaster raster = CvRaster.manage(in);
            switch (cvDepth) {

                case CV_8U:
                    getter = (r, c) -> {
                        in.get(r, c, tbbuf);
                        return Byte.toUnsignedInt(tbbuf[0]);
                    };
                case CV_8S: {
                    if (cvDepth == CV_8S)
                        getter = (r, c) -> {
                            in.get(r, c, tbbuf);
                            return (int) tbbuf[0];
                        };
                    final RGB[] rgb = new RGB[256];
                    for (int i = 0; i < 256; i++)
                        rgb[i] = new RGB(i, i, i);
                    pd = new PaletteData(rgb);
                    depth = 8;
                    break;
                }
                case CV_16U:
                    getter = (r, c) -> {
                        return Short.toUnsignedInt(((short[]) raster.get(r, c))[0]);
                    };
                case CV_16S: {
                    if (cvDepth == CV_16S)
                        getter = (r, c) -> {
                            in.get(r, c, tsbuf);
                            return (int) tsbuf[0];
                        };
                    pd = new PaletteData(255, 255, 255);
                    depth = 16;
                    break;
                }
                default:
                    throw new IllegalArgumentException(
                            "Cannot convert a Mat with a type of " + CvType.typeToString(in.type()) + " to a BufferedImage");
            }
            final ImageData id = new ImageData(in.width(), in.height(), depth, pd);
            for (int row = 0; row < height; row++)
                for (int col = 0; col < width; col++)
                    id.setPixel(col, row, getter.get(row, col));
            return id;
        } else if (inChannels == 3) {
            if (cvDepth != CV_8U && cvDepth != CV_8S)
                throw new IllegalArgumentException("Cannot convert RGB Mat to SWT image with elements larger than a byte yet.");
            final ImageData id = new ImageData(width, height, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
            final byte[] pixel = new byte[3];
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    in.get(row, col, pixel);
                    final int pix = Byte.toUnsignedInt(pixel[0]) | 0xff00 & (Byte.toUnsignedInt(pixel[1]) << 8)
                            | 0xff0000 & (Byte.toUnsignedInt(pixel[2]) << 16);
                    id.setPixel(col, row, pix);
                }
            }
            return id;
        } else if (inChannels == 4)
            throw new IllegalArgumentException("Can't handle alpha channel yet.");
        else
            throw new IllegalArgumentException("Can't handle an image with " + inChannels + " channels");
    }
}
