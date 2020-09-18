package ai.kognition.pilecv4j.image.display.swt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Listener;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public abstract class SwtCanvasImageDisplay extends ImageDisplay {
    private static final Logger LOGGER = LoggerFactory.getLogger(SwtCanvasImageDisplay.class);

    // Event callbacks
    protected KeyPressCallback callback;
    protected Listener closeCallback;
    protected SelectCallback selectCallback;

    protected final AtomicReference<CvMat> currentImageRef = new AtomicReference<CvMat>(null);
    protected final AtomicBoolean done = new AtomicBoolean(false);
    public Canvas canvas;
    // protected Display display;
    protected Composite parent;
    protected final CountDownLatch waitUntilClosedLatch = new CountDownLatch(1);

    // These are for tracking changes in the bounds which reqiures
    // a repacking of the layouts.
    private Size prevBounds = null;
    final AtomicBoolean alreadySized = new AtomicBoolean(false);

    public SwtCanvasImageDisplay() {}

    @Override
    public void setCloseCallback(final Runnable closeCallback) {
        removeCurrentCloseCallback();
        this.closeCallback = e -> closeCallback.run();
        if(closeCallback != null) {
            ImageDisplay.syncExec(() -> {
                canvas.addListener(SWT.Dispose, this.closeCallback);
            });
        }
    }

    private void removeCurrentCloseCallback() {
        if(this.closeCallback != null) {
            ImageDisplay.syncExec(() -> {
                canvas.removeListener(SWT.Dispose, this.closeCallback);
            });
        }
    }

    protected void setup(final Canvas canvas, final Runnable closeCallback, final KeyPressCallback kpCallback,
        final SelectCallback selectCallback) {
        this.canvas = canvas;
        this.parent = canvas.getParent();
        // this.display = canvas.getDisplay();

        this.callback = kpCallback;
        this.selectCallback = selectCallback;

        ImageDisplay.syncExec(() -> {
            if(selectCallback != null) {
                canvas.addMouseListener(new MouseListener() {

                    @Override
                    public void mouseUp(final MouseEvent e) {}

                    @Override
                    public void mouseDown(final MouseEvent e) {
                        // origin is negative when scrolled since it's in that direction from the origin of the viewport.
                        if(selectCallback.select(canvasLocationToImageLocation(e.x, e.y))) {
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

            canvas.addListener(SWT.Dispose, e -> close());
        });

        setCloseCallback(closeCallback);
    }

    public abstract org.opencv.core.Point canvasLocationToImageLocation(int x, int y);

    @Override
    public void close() {
        if(!done.get()) {
            done.set(true);
            ImageDisplay.syncExec(() -> {
                if(canvas != null) {
                    canvas.dispose();
                    final CvMat img = currentImageRef.getAndSet(null);
                    if(img != null)
                        img.close();
                }
            });

            waitUntilClosedLatch.countDown();
        }
    }

    @Override
    public void update(final Mat image) {
        LOGGER.trace("Showing image {}", image);
        ImageDisplay.syncExec(() -> {
            try(final CvMat prev = currentImageRef.getAndSet(CvMat.shallowCopy(image));) {}
            final Size bounds = image.size();
            if(!bounds.equals(prevBounds)) {
                // shell.setSize(bounds.width, bounds.height);
                alreadySized.set(false);
                prevBounds = bounds;
            }
            if(canvas != null && !canvas.isDisposed())
                canvas.redraw();
        });
    }

    @Override
    public void waitUntilClosed() throws InterruptedException {
        waitUntilClosedLatch.await();
    }

}
