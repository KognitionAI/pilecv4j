/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.image.display;

import static net.dempsy.util.Functional.chain;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.display.swt.SwtImageDisplay;
import ai.kognition.pilecv4j.image.display.swt.SwtImageDisplay.CanvasType;

public abstract class ImageDisplay implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageDisplay.class);
    private static final AtomicLong sequence = new AtomicLong(0);
    private static Thread executorThread;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(
        r -> chain(executorThread = new Thread(r, "ImageDisplay Main Thread"), t -> t.setDaemon(true)));

    public static final String DEFAULT_WINDOWS_NAME_PREFIX = "Window";
    public static final Implementation DEFAULT_IMPLEMENTATION = Implementation.HIGHGUI;

    private static final List<Runnable> eventPolling = new CopyOnWriteArrayList<>();
    private static Thread eventPollingEmitterLoop = null;

    public static synchronized void addEventPollingRunnable(final Runnable eventPoller) {
        eventPolling.add(eventPoller);
        if(eventPollingEmitterLoop == null) {
            eventPollingEmitterLoop = new Thread(() -> {
                int curIndex = 0;
                while(true) {
                    final int numPollingRunnables = eventPolling.size();
                    if(numPollingRunnables == 0)
                        Functional.uncheck(() -> Thread.sleep(1));
                    else {
                        if(curIndex >= eventPolling.size())
                            curIndex = 0;

                        final Runnable curRunnable = eventPolling.get(curIndex++);
                        try {
                            curRunnable.run();
                        } catch(final Throwable th) {
                            LOGGER.warn("Exception thrown by {} when polling for events. But yet, I'm continuing on.", curRunnable, th);
                        }
                    }
                }
            }, "ImageDisplay Event Loop Emitter");
            eventPollingEmitterLoop.setDaemon(true);
            eventPollingEmitterLoop.start();
        }
    }

    public static void syncExec(final Runnable eventHandler) {
        if(eventHandler == null)
            throw new NullPointerException("Cannot pass a null Runnable to " + ImageDisplay.class.getSimpleName() + ".syncExec.");

        if(Thread.currentThread() == executorThread) {
            try {
                eventHandler.run();
            } catch(final RuntimeException rte) {
                LOGGER.info("Exception processing {} event.", ImageDisplay.class.getSimpleName(), rte);
            }
        } else {
            try {
                final Future<?> future = executor.submit(() -> {
                    // can only throw a RuntimeException
                    eventHandler.run();
                });

                try {
                    future.get();
                } catch(final ExecutionException e) {
                    // the eventHandler can only throw a RuntimeException
                    throw new RuntimeException(e.getCause());
                }

            } catch(final RuntimeException rte) {
                LOGGER.info("Exception processing {} event.", ImageDisplay.class.getSimpleName(), rte);
            } catch(final InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }

    public static void asyncExec(final Runnable eventHandler) {
        executor.submit(eventHandler);
    }

    public abstract void update(final Mat toUpdate);

    public abstract void waitUntilClosed() throws InterruptedException;

    public abstract void setCloseCallback(Runnable closeCallback);

    @FunctionalInterface
    public static interface KeyPressCallback {
        public boolean keyPressed(int keyPressed);
    }

    @FunctionalInterface
    public static interface SelectCallback {
        public boolean select(Point pointClicked);
    }

    private static class Proxy extends ImageDisplay {
        protected final ImageDisplay underlying;

        private Proxy(final ImageDisplay underlying) {
            this.underlying = underlying;
        }

        @Override
        public void close() {
            underlying.close();
        }

        @Override
        public void update(final Mat toUpdate) {
            underlying.update(toUpdate);
        }

        @Override
        public void waitUntilClosed() throws InterruptedException {
            underlying.waitUntilClosed();
        }

        @Override
        public void setCloseCallback(final Runnable closeCallback) {
            underlying.setCloseCallback(closeCallback);
        }
    }

    /**
     * SWT defaults to SWT_SCROLLABLE
     */
    public static enum Implementation {
        HIGHGUI, SWT, SWT_SCROLLABLE, SWT_RESIZABLE
    }

    public static class Builder {
        private KeyPressCallback keyPressHandler = null;
        private Implementation implementation = DEFAULT_IMPLEMENTATION;
        private Runnable closeCallback = null;
        private Mat toShow = null;
        private String windowName = DEFAULT_WINDOWS_NAME_PREFIX + "_" + sequence.incrementAndGet();
        private SelectCallback selectCallback = null;
        private Size screenDim = null;
        private boolean preserveAspectRatio = true;

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

        public Builder dim(final Size screenDim) {
            return dim(screenDim, true);
        }

        public Builder dim(final Size screenDim, final boolean preserveAspectRatio) {
            this.screenDim = screenDim;
            this.preserveAspectRatio = preserveAspectRatio;
            return this;
        }

        public ImageDisplay build() {
            if(screenDim == null)
                return dobuild();
            else
                return new Proxy(dobuild()) {
                    private Size adjustedSize = null;

                    @Override
                    public void update(final Mat mat) {
                        if(adjustedSize == null)
                            adjustedSize = preserveAspectRatio ? Utils.scaleDownOrNothing(mat, screenDim)
                                : screenDim;

                        try(CvMat tmat = CvMat.shallowCopy(mat);
                            CvMat resized = new CvMat();) {
                            Imgproc.resize(mat, resized, adjustedSize, 0, 0, Imgproc.INTER_NEAREST);
                            underlying.update(resized);
                        }
                    }

                };
        }

        protected ImageDisplay dobuild() {
            switch(implementation) {
                case HIGHGUI: {
                    if(selectCallback != null)
                        LOGGER.info("The select callback will be ignored when using the HIGHGUI implementation of ImageDisplay");
                    return new CvImageDisplay(toShow, windowName, closeCallback, keyPressHandler);
                }
                case SWT:
                case SWT_SCROLLABLE:
                    return new SwtImageDisplay(toShow, windowName, closeCallback, keyPressHandler, selectCallback, CanvasType.SCROLLABLE);
                case SWT_RESIZABLE:
                    return new SwtImageDisplay(toShow, windowName, closeCallback, keyPressHandler, selectCallback, CanvasType.RESIZABLE);
                default:
                    throw new IllegalStateException();
            }
        }
    }

    public static CvMat displayable(final Mat mat) {
        final int type = mat.type();

        final int depth = CvType.depth(type);
        if(depth == CvType.CV_8U || depth == CvType.CV_8S)
            return CvMat.shallowCopy(mat);

        final int inChannels = mat.channels();
        if(inChannels != 1 && inChannels != 3)
            throw new IllegalArgumentException("Cannot handle an image of type " + CvType.typeToString(type) + "  yet.");

        final int newType = (inChannels == 1) ? CvType.CV_8UC1 : CvType.CV_8UC3;
        try(CvMat ret = new CvMat()) {

            mat.convertTo(ret, newType, 1.0 / 256.0);
            return ret.returnMe();
        }
    }

    public static void main(final String[] args) throws Exception {
        try(final ImageDisplay id = new Builder()
            .implementation(Implementation.SWT)
            .build();) {
            String string = (args.length > 0 ? args[0] : null);
            if(string == null) {
                final Display display = new Display();
                final Shell shell = new Shell(display);
                try(QuietCloseable c1 = () -> display.close();
                    QuietCloseable c2 = () -> shell.close()) {
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
