package ai.kognition.pilecv4j.ffmpeg;

import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_DEBUG;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_ERROR;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_FATAL;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_INFO;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_TRACE;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.LOG_LEVEL_WARN;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.sun.jna.Pointer;

import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.fill_buffer_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.push_frame_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.seek_buffer_callback;
import ai.kognition.pilecv4j.image.VideoFrame;

public class Ffmpeg {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ffmpeg.class);

    public static final int AVERROR_EOF = FfmpegApi.pcv4j_ffmpeg_code_averror_eof();

    // values of 'whence' passed to seek_buffer_callback
    public static final int SEEK_SET = FfmpegApi.pcv4j_ffmpeg_code_seek_set();
    public static final int SEEK_CUR = FfmpegApi.pcv4j_ffmpeg_code_seek_cur();
    public static final int SEEK_END = FfmpegApi.pcv4j_ffmpeg_code_seek_end();
    public static final int AVSEEK_SIZE = FfmpegApi.pcv4j_ffmpeg_code_seek_size();
    public static final int AVEAGAIN = FfmpegApi.pcv4j_ffmpeg_code_eagain();

    static {
        FfmpegApi._init();
    }

    @FunctionalInterface
    public static interface VideoFrameConsumer {
        public void handle(VideoFrame frame);
    }

    @FunctionalInterface
    public static interface VideoDataSupplier {
        public int fillBuffer(ByteBuffer buf, int numBytes);
    }

    @FunctionalInterface
    public static interface VideoDataSeek {
        public long seekBuffer(ByteBuffer buf, long offset, int whence);
    }

    public static class StreamContext implements QuietCloseable {

        public final long nativeDef;
        private final AtomicLong frameNumber = new AtomicLong(0);

        // ======================================================================
        // JNA will only hold a weak reference to the callbacks passed in
        // so if we dynamically allocate them then they will be garbage collected.
        // In order to prevent that we're keeping strong references to them.
        // These are not private in order to avoid any possibility that the
        // JVM optimized them out since they aren't read anywhere in this code.
        fill_buffer_callback strongRefDs;
        seek_buffer_callback strongRefS;
        push_frame_callback pfc;
        // ======================================================================

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

        public StreamContext setSource(final VideoDataSupplier dataSupplier, final VideoDataSeek seeker) {
            final ByteBuffer buffer = customStreamBuffer();

            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_set_custom_source(nativeDef,
                strongRefDs = new fill_buffer_callback() {
                    @Override
                    public int fill_buffer(final int numBytes) {
                        return dataSupplier.fillBuffer(buffer, numBytes);
                    }
                },

                strongRefS = (seeker != null ? new seek_buffer_callback() {
                    @Override
                    public long fill_buffer(final long offset, final int whence) {
                        return seeker.seekBuffer(buffer, offset, whence);
                    }
                } : null)));

            return this;
        }

        public StreamContext setLogLevel(final Logger logger) {
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
            return this;
        }

        public StreamContext sync(final boolean sync) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_set_sync(nativeDef, sync ? 1 : 0));
            return this;
        }

        public StreamContext addOption(final String key, final String value) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_add_option(nativeDef, key, value));
            return this;
        }

        public StreamContext addOptions(final Map<String, String> options) {
            options.entrySet().stream().forEach(e -> addOption(e.getKey(), e.getValue()));
            return this;
        }

        public StreamContext setFrameHandler(final VideoFrameConsumer consumer) {
            if(pfc != null)
                throw new IllegalStateException("Can't set frame handler twice.");
            pfc = new push_frame_callback() {

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
            };

            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_set_frame_handler(nativeDef, pfc));
            return this;
        }

        public synchronized StreamContext stop() {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_stop(nativeDef));
            return this;
        }

        private ByteBuffer customStreamBuffer() {
            final Pointer value = FfmpegApi.pcv4j_ffmpeg_customStreamBuffer(nativeDef);
            final int bufSize = FfmpegApi.pcv4j_ffmpeg_customStreamBufferSize(nativeDef);
            return value.getByteBuffer(0, bufSize);
        }

        public StreamContext play() {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_play(nativeDef), Ffmpeg.AVERROR_EOF);
            return this;
        }

        public StreamContext addRemuxer(final String fmt, final String outputUri) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_add_remuxer(nativeDef, fmt, outputUri));
            return this;
        }

        public StreamContext addRemuxer(final String outputUri) {
            return addRemuxer(determineFormatFromUri(outputUri), outputUri);
        }

        public StreamContext setSource(final String url) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_set_source(nativeDef, url));
            return this;
        }

        public StreamContext setMaxRemuxErrorCount(final int maxRemuxErrorCount) {
            throwIfNecessary(FfmpegApi.pcv4j_ffmpeg_max_remux_error_count(nativeDef, maxRemuxErrorCount));
            return this;
        }

        public StreamContext setSource(final URI url) {
            return setSource(url.toString());
        }

        public StreamContext optionally(final boolean doIt, final Consumer<StreamContext> whatToDo) {
            if(doIt)
                whatToDo.accept(this);
            return this;
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

    private static void throwIfNecessary(final long status, final long... ignore) {
        final Set<Long> toIgnore = Arrays.stream(ignore).mapToObj(Long::valueOf).collect(Collectors.toSet());
        if(status != 0L && !toIgnore.contains(status)) {
            throw new FfmpegException(status, errorMessage(status));
        }
    }

    private static String determineFormatFromUri(final String uri) {
        try {
            final URI u = new URI(uri);
            if("rtmp".equals(u.getScheme()))
                return "flv";
            else if("rtsp".equals(u.getScheme()))
                return "mp4";
            else
                return null;
        } catch(final URISyntaxException uris) {
            // assume this is just a filename with no schema
            return null; // this is a valid format for ffmpeg given a valid extention on the filename.
        }
    }

}
