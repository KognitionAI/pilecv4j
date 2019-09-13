package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.chain;

import java.util.concurrent.CountDownLatch;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.MutableRef;

import ai.kognition.pilecv4j.gstreamer.BreakoutFilter.CvMatAndCaps;
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
    private ImageDisplay window = null;
    private Pipeline stopOnClose = null;
    private CountDownLatch stopLatch = null;
    private final Size screenDim;

    private final MutableRef<Size> adjustedSize = new MutableRef<>();

    public InlineDisplay(final ImageDisplay display) {
        this(display, DEFAULT_DEEP_COPY);
    }

    public InlineDisplay(final ImageDisplay display, final boolean deepCopy) {
        this.window = display;
        screenDim = null;
        this.deepCopy = deepCopy;
        this.window.setCloseCallback(() -> {
            if(stopOnClose != null) {
                GstUtils.stopBinOnEOS(stopOnClose, stopLatch);
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
                    GstUtils.stopBinOnEOS(stopOnClose, stopLatch);
                    LOGGER.debug("Emitting EOS");
                    stopOnClose.sendEvent(new EOSEvent());
                }
            })
            .keyPressHandler(kpc)
            .build();

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

    private static final Caps rgbFormatCaps = new Caps("video/x-raw,format=RGB");

    @Override
    public void accept(final CvMatAndCaps rac) {
        final VideoFrame mx = rac.mat;
        final Caps caps = rac.caps;

        final boolean convertToBgr = caps != null && !caps.intersect(rgbFormatCaps).isEmpty();

        try (final Closer closer = new Closer();) {
            if(screenDim != null) {
                if(adjustedSize.ref == null) {
                    adjustedSize.ref = Utils.preserveAspectRatio(mx, screenDim);
                }

                final VideoFrame lmat = chain(closer.add(new VideoFrame(mx.pts, mx.dts, mx.duration, mx.decodeTimeMillis)),
                    m -> Imgproc.resize(mx, m, adjustedSize.ref, -1, -1, Imgproc.INTER_NEAREST));
                final VideoFrame lmat2 = convertToBgr
                    ? chain(closer.add(new VideoFrame(mx.pts, mx.dts, mx.duration, mx.decodeTimeMillis)), m -> Imgproc.cvtColor(lmat, m, Imgproc.COLOR_RGB2BGR))
                    : lmat;
                process(lmat2);
            } else {
                final VideoFrame lmat = convertToBgr
                    ? chain(closer.add(new VideoFrame(mx.pts, mx.dts, mx.duration, mx.decodeTimeMillis)), m -> Imgproc.cvtColor(mx, m, Imgproc.COLOR_RGB2BGR))
                    : (deepCopy ? mx.deepCopy() : mx.shallowCopy());
                process(lmat);
            }
        }
    }

    public void stopOnClose(final Pipeline pipe, final CountDownLatch stopLatch) {
        this.stopOnClose = pipe;
        this.stopLatch = stopLatch;
    }

    private void process(final VideoFrame lmat) {
        window.update(lmat);
    }
}
