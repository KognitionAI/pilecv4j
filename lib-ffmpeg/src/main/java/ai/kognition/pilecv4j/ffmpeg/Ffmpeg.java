package ai.kognition.pilecv4j.ffmpeg;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.jna.Pointer;

import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.push_frame_callback;
import ai.kognition.pilecv4j.image.VideoFrame;

public class Ffmpeg {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ffmpeg.class);

    static {
        FfmpegApi._init();

        if(LOGGER.isTraceEnabled())
            enableLogging();
    }

    @FunctionalInterface
    public static interface VideoFrameConsumer {
        public void handle(VideoFrame frame);
    }

    public static class StreamContext implements QuietCloseable {

        public final long nativeDef;
        private final AtomicLong frameNumber = new AtomicLong(0);

        StreamContext(final long nativeDef) {
            if(nativeDef == 0)
                throw new FfmpegException("Couldn't create new stream context");

            this.nativeDef = nativeDef;
        }

        @Override
        public void close() {
            if(nativeDef != 0)
                FfmpegApi.ffmpeg_deleteContext(nativeDef);
        }

        public void openStream(final URI url) {
            openStream(url.toString());
        }

        public void openStream(final String url) {
            throwIfNecessary(FfmpegApi.ffmpeg_openStream(nativeDef, url));
            throwIfNecessary(FfmpegApi.ffmpeg_findFirstVideoStream(nativeDef));
        }

        public void processFrames(final VideoFrameConsumer consumer) {

            FfmpegApi.process_frames(nativeDef, new push_frame_callback() {

                @Override
                public void push_frame(final long frame, final int isRbg) {
                    try(final VideoFrame mat = new VideoFrame(
                        frame, System.currentTimeMillis(), frameNumber.getAndIncrement(), isRbg == 0 ? false : true) {

                        // mats are closed automatically in the native code
                        // once the push_frame returns.
                        @Override
                        public void doNativeDelete() {}
                    };) {
                        consumer.handle(mat);
                    }
                }
            });
        }
    }

    public static String errorMessage(final long errorCode) {
        final MutableObject<Pointer> nmes = new MutableObject<>(null);
        try(final QuietCloseable qc = () -> FfmpegApi.ffmpeg_freeString(nmes.getValue());) {
            nmes.setValue(FfmpegApi.ffmpeg_statusMessage(errorCode));
            return Optional.ofNullable(nmes.getValue()).orElseThrow(() -> new FfmpegException("Failed to retrieve status message for code: " + errorCode))
                .getString(0);
        }
    }

    public static StreamContext createStreamContext() {
        return new StreamContext(FfmpegApi.ffmpeg_createContext());
    }

    public static boolean enableLogging() {
        return enableLogging(true);
    }

    public static boolean enableLogging(final boolean doIt) {
        final long result = FfmpegApi.enable_logging(doIt ? 1 : 0);
        if(result != 0L) {
            LOGGER.warn("Failed to enable logging: ", errorMessage(result));
            return false;
        }
        return true;
    }

    private static void throwIfNecessary(final long status) {
        if(status != 0L) {
            throw new FfmpegException(status, errorMessage(status));
        }
    }

}
