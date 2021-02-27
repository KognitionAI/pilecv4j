package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.chain;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.MutableRef;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;
import ai.kognition.pilecv4j.image.CvRaster.Closer;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.KeyPressCallback;

public class InlineDisplay implements BreakoutFilter.VideoFrameFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlineDisplay.class);

    public static String DEFAULT_WINDOW_NAME = "inline-display";
    public static boolean DEFAULT_DEEP_COPY = true;

    private final boolean deepCopy;
    private final ImageDisplay window;
    private final boolean ownsDisplay;
    private final Size screenDim;

    private Pipeline stopOnClose = null;
    private Runnable exitNotification = null;

    private final MutableRef<Size> adjustedSize = new MutableRef<>();

    public InlineDisplay(final ImageDisplay display, final boolean ownsDisplay) {
        this(display, ownsDisplay, DEFAULT_DEEP_COPY);
    }

    public InlineDisplay(final ImageDisplay display, final boolean ownsDisplay, final boolean deepCopy) {
        this.window = display;
        this.ownsDisplay = ownsDisplay;
        screenDim = null;
        this.deepCopy = deepCopy;
        this.window.setCloseCallback(() -> {
            if(stopOnClose != null) {
                GstUtils.stopBinOnEOS(stopOnClose, exitNotification);
                LOGGER.debug("Emitting EOS");
                stopOnClose.sendEvent(new EOSEvent());
            }
        });
    }

    private InlineDisplay(final Size screenDim, final boolean deepCopy, final KeyPressCallback kpc, final boolean preserveAspectRatio,
        final ImageDisplay.Implementation impl, final String windowName) {
        this.deepCopy = deepCopy;
        this.screenDim = screenDim;

        if(!preserveAspectRatio)
            adjustedSize.ref = screenDim;

        window = new ImageDisplay.Builder()
            .windowName(windowName)
            .implementation(impl)
            .closeCallback(() -> {
                if(stopOnClose != null) {
                    GstUtils.stopBinOnEOS(stopOnClose, exitNotification);
                    LOGGER.debug("Emitting EOS");
                    stopOnClose.sendEvent(new EOSEvent());
                }
            })
            .keyPressHandler(kpc)
            .build();

        ownsDisplay = true;
    }

    public static class Builder {

        private boolean deepCopy = DEFAULT_DEEP_COPY;
        private KeyPressCallback kpc = null;
        private Size screenDim = null;
        private boolean preserveAspectRatio = true;
        private ImageDisplay.Implementation impl = ImageDisplay.DEFAULT_IMPLEMENTATION;
        private String windowName = DEFAULT_WINDOW_NAME;

        public Builder deepCopy(final boolean deepCopy) {
            this.deepCopy = deepCopy;
            return this;
        }

        public Builder keyPressCallback(final KeyPressCallback kpc) {
            this.kpc = kpc;
            return this;
        }

        public Builder dim(final Size screenDim) {
            this.screenDim = screenDim;
            return this;
        }

        public Builder preserveAspectRatio(final boolean preserveAspectRatio) {
            this.preserveAspectRatio = preserveAspectRatio;
            return this;
        }

        public Builder implementation(final ImageDisplay.Implementation impl) {
            this.impl = impl;
            return this;
        }

        public Builder windowName(final String windowName) {
            this.windowName = windowName;
            return this;
        }

        public InlineDisplay build() {
            return new InlineDisplay(screenDim, deepCopy, kpc, preserveAspectRatio, impl, windowName);
        }
    }

    @Override
    public void accept(final VideoFrame rac) {
        final VideoFrame mx = rac;

        try(final Closer closer = new Closer();) {
            if(screenDim != null) {
                if(adjustedSize.ref == null) {
                    adjustedSize.ref = Utils.scaleDownOrNothing(mx, screenDim);
                }

                final VideoFrame lmat = chain(closer.add(new VideoFrame(mx.decodeTimeMillis, rac.frameNumber)),
                    m -> Imgproc.resize(mx, m, adjustedSize.ref, -1, -1, Imgproc.INTER_NEAREST));
                process(lmat);
            } else {
                final VideoFrame lmat = (deepCopy ? mx.deepCopy() : mx.shallowCopy());
                process(lmat);
            }
        }
    }

    @Override
    public void close() {
        if(window != null && ownsDisplay)
            window.close();
    }

    public void stopOnClose(final Pipeline pipe, final Runnable exitNotification) {
        this.stopOnClose = pipe;
        this.exitNotification = exitNotification;
    }

    private void process(final VideoFrame lmat) {
        window.update(lmat);
    }
}
