package ai.kognition.pilecv4j.ffmpeg;

import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_DEBUG;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_ERROR;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_FATAL;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_INFO;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_TRACE;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_WARN;

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
            setLogLevel(LOGGER);
        }

        @Override
        public void close() {
            if(nativeDef != 0)
                FfmpegApi.pcv4j_ffmpeg_deleteContext(nativeDef);
        }

        public void openStream(final URI url) {
            openStream(url.toString());
        }

        public void openStream(final String url) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_openStream(nativeDef, url));
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_findFirstVideoStream(nativeDef));
        }

        public void setLogLevel(final Logger logger) {
            // find the level
            final int logLevelSet;
            if(logger.isTraceEnabled())
                logLevelSet = LOG_LEVEL_TRACE;
            else if(logger.isDebugEnabled())
                logLevelSet = LOG_LEVEL_DEBUG;
            else if(logger.isInfoEnabled())
                logLevelSet = LOG_LEVEL_INFO;
            else if(logger.isWarnEnabled())
                logLevelSet = LOG_LEVEL_WARN;
            else if(logger.isErrorEnabled())
                logLevelSet = LOG_LEVEL_ERROR;
            else
                logLevelSet = LOG_LEVEL_FATAL;

            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_set_log_level(nativeDef, logLevelSet));
        }

        public void sync(final boolean sync) {
            FfmpegApi.pcv4j_ffmpeg_set_syc(nativeDef, sync ? 1 : 0);
        }

        public void addOption(final String key, final String value) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_add_option(nativeDef, key, value));
        }

        public void processFrames(final VideoFrameConsumer consumer) {

            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_process_frames(nativeDef, new push_frame_callback() {

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
            }));
        }

        public synchronized void stop() {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_stop(nativeDef));
        }

    }

    public static String errorMessage(final long errorCode) {
        final MutableObject<Pointer> nmes = new MutableObject<>(null);
        try(final QuietCloseable qc = () -> FfmpegApi.pcv4j_ffmpeg_freeString(nmes.getValue());) {
            nmes.setValue(FfmpegApi.pcv4j_ffmpeg_statusMessage(errorCode));
            return Optional.ofNullable(nmes.getValue()).orElseThrow(() -> new FfmpegException("Failed to retrieve status message for code: " + errorCode))
                .getString(0);
        }
    }

    public static StreamContext createStreamContext() {
        return new StreamContext(FfmpegApi.pcv4j_ffmpeg_createContext());
    }

    private static void throwIfNecessary(final long status) {
        if(status != 0L) {
            throw new FfmpegException(status, errorMessage(status));
        }
    }

}
