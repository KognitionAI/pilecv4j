package ai.kognition.pilecv4j.image.display;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;

public class CvImageDisplay extends ImageDisplay {
    static {
        CvMat.initOpenCv();
    }

    private static Logger LOGGER = LoggerFactory.getLogger(CvImageDisplay.class);

    // ==============================================================
    // This is basically a single threaded executor but we need to
    // check cv::waitKey or nothing happens in OpenCv::HighGUI
    private static ArrayBlockingQueue<Consumer<WindowsState>> commands = new ArrayBlockingQueue<>(2);
    public static AtomicBoolean stillRunningEvents = new AtomicBoolean(true);

    private final CountDownLatch countDown = new CountDownLatch(1);

    @FunctionalInterface
    private static interface CvKeyPressCallback {
        public String keyPressed(int keyPressed);
    }

    private static class WindowsState {
        final Map<String, CvImageDisplay> windows = new HashMap<>();
        final Map<String, CvKeyPressCallback> callbacks = new HashMap<>();

        void remove(final String n) {
            callbacks.remove(n);
            windows.remove(n);
        }
    }

    static {
        final WindowsState state = new WindowsState();

        ImageDisplay.addEventPollingRunnable(() -> {
            if(stillRunningEvents.get()) {
                ImageDisplay.syncExec(() -> {
                    // System.out.println(state.windows);
                    try {
                        if(state.windows.size() > 0) {
                            // then we can check for a key press.
                            final int key = ImageAPI.CvRaster_fetchEvent(1);
                            final Set<String> toCloseUp = state.callbacks.values().stream()
                                .map(cb -> cb.keyPressed(key))
                                .filter(n -> n != null)
                                .collect(Collectors.toSet());

                            toCloseUp.addAll(state.windows.keySet().stream()
                                .filter(ImageAPI::CvRaster_isWindowClosed)
                                .collect(Collectors.toSet()));

                            toCloseUp.forEach(n -> {
                                // need to close the window and cleanup.
                                ImageAPI.CvRaster_destroyWindow(n);
                                final CvImageDisplay id = state.windows.get(n);
                                if(id != null)
                                    id.closeNow.set(true);

                                state.remove(n);
                                if(id != null)
                                    id.close();
                            });
                        } else
                            ignore(() -> Thread.sleep(1));

                        // we need to check to see if there's any commands to execute.
                        final Consumer<WindowsState> cmd = commands.poll();
                        if(cmd != null) {
                            try {
                                cmd.accept(state);
                            } catch(final Exception e) {
                                LOGGER.error("OpenCv::HighGUI command \"{}\" threw an excetion.", cmd, e);
                            }
                        } else
                            ignore(() -> Thread.sleep(1));

                    } catch(final Throwable th) {
                        LOGGER.error("OpenCv::HighGUI CRITICAL ERROR! But yet, I persist.", th);
                    }
                });
            }

        });

    }
    // ==============================================================

    private boolean closed = false;
    private final ShowKeyPressCallback callback;
    private Runnable closeCallback;
    private final String name;
    private boolean shownYet = false;
    private final AtomicBoolean closeNow = new AtomicBoolean(false);

    CvImageDisplay(final Mat mat, final String name, final Runnable closeCallback, final KeyPressCallback kpCallback) {

        this.closeCallback = closeCallback;
        this.name = name;

        // create a callback that ignores the keypress but polls the state of the closeNow
        this.callback = new ShowKeyPressCallback(this, kpCallback);

        if(mat != null) {
            doShow(mat, name, callback);

            while(!callback.shown.get())
                Thread.yield();
        }
    }

    @Override
    public void setCloseCallback(final Runnable closeCallback) {
        this.closeCallback = closeCallback;
    }

    @Override
    public void waitUntilClosed() throws InterruptedException {
        countDown.await();
    }

    @Override
    public void close() {
        if(!closed) {
            LOGGER.trace("Closing window \"{}\"", name);
            countDown.countDown();
            closed = true;
            closeNow.set(true);
            if(closeCallback != null)
                closeCallback.run();
        }
    }

    @Override
    public void update(final Mat toUpdate) {
        if(closed) {
            LOGGER.trace("Attempting to update a closed window with {}", toUpdate);
            return;
        }

        if(closeNow.get() && !closed) {
            close();
            LOGGER.debug("Attempting to update a closed window with {}", toUpdate);
            return;
        }

        if(!shownYet) {
            synchronized(this) {
                if(!shownYet)
                    doShow(toUpdate, name, callback);
            }
        }

        // Shallow copy
        final CvMat old = callback.update.getAndSet(CvMat.shallowCopy(toUpdate));
        if(old != null)
            old.close();
    }

    private void doShow(final Mat mat, final String name, final CvKeyPressCallback callback) {
        LOGGER.trace("Showing image {} in window {} ", mat, name);

        // There is a problem with resource management since the mat is being passed to another thread
        final CvMat omat = CvMat.shallowCopy(mat);
        uncheck(() -> commands.put(s -> {
            try (CvMat lmat = omat) {
                ImageAPI.CvRaster_showImage(name, omat.nativeObj);
                // if we got here, we're going to assume the windows was created.
                if(callback != null)
                    s.callbacks.put(name, callback);
                s.windows.put(name, this);
            }
        }));
        shownYet = true;
    }

    // a callback that ignores the keypress but polls the state of the closeNow
    private static class ShowKeyPressCallback implements CvKeyPressCallback {
        final AtomicBoolean shown = new AtomicBoolean(false);
        final AtomicReference<CvMat> update = new AtomicReference<>(null);
        final KeyPressCallback keyPressCallback;
        final CvImageDisplay window;

        private boolean shownSet = false;

        private ShowKeyPressCallback(final CvImageDisplay window, final KeyPressCallback keyPressCallback) {
            this.window = window;
            this.keyPressCallback = keyPressCallback;
        }

        @Override
        public String keyPressed(final int kp) {
            // the window is shown by the time we get here.
            if(!shownSet) {
                shown.set(true);
                shownSet = true;
            }

            try (final CvMat toUpdate = update.getAndSet(null);) {
                if(toUpdate != null)
                    ImageAPI.CvRaster_updateWindow(window.name, toUpdate.nativeObj);
            }

            if(keyPressCallback != null && kp >= 0) {
                if(keyPressCallback.keyPressed(kp))
                    window.closeNow.set(true);
            } else if(kp == 32) window.closeNow.set(true);

            return window.closeNow.get() ? window.name : null;
        }
    }
}
