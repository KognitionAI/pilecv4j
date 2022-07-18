package ai.kognition.pilecv4j.ffmpeg;

import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_DEBUG;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_ERROR;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_FATAL;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_INFO;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_TRACE;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_WARN;
import static net.dempsy.util.Functional.ignore;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import org.apache.commons.lang3.mutable.MutableObject;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.StreamContext.StreamDetails;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.fill_buffer_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.push_frame_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.seek_buffer_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.select_streams_callback;
import ai.kognition.pilecv4j.image.VideoFrame;

public class Ffmpeg2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(Ffmpeg2.class);

    static {
        FfmpegApi2._init();
    }

    public static final long AVERROR_EOF_KOGSTAT = FfmpegApi2.pcv4j_ffmpeg_code_averror_eof_as_kognition_stat();
    public static final long AVERROR_UNKNOWN = FfmpegApi2.pcv4j_ffmpeg_code_averror_unknown_as_kognition_stat();
    public static final int AVERROR_EOF_AVSTAT = FfmpegApi2.pcv4j_ffmpeg_code_averror_eof();

    // values of 'whence' passed to seek_buffer_callback
    public static final int SEEK_SET = FfmpegApi2.pcv4j_ffmpeg_code_seek_set();
    public static final int SEEK_CUR = FfmpegApi2.pcv4j_ffmpeg_code_seek_cur();
    public static final int SEEK_END = FfmpegApi2.pcv4j_ffmpeg_code_seek_end();
    public static final int AVSEEK_SIZE = FfmpegApi2.pcv4j_ffmpeg_code_seek_size();
    public static final int AVEAGAIN = FfmpegApi2.pcv4j_ffmpeg_code_eagain();

    public static final int AVMEDIA_TYPE_UNKNOWN = FfmpegApi2.pcv4j_ffmpeg2_mediaType_UNKNOWN();
    public static final int AVMEDIA_TYPE_VIDEO = FfmpegApi2.pcv4j_ffmpeg2_mediaType_VIDEO();
    public static final int AVMEDIA_TYPE_AUDIO = FfmpegApi2.pcv4j_ffmpeg2_mediaType_AUDIO();
    public static final int AVMEDIA_TYPE_DATA = FfmpegApi2.pcv4j_ffmpeg2_mediaType_DATA();
    public static final int AVMEDIA_TYPE_SUBTITLE = FfmpegApi2.pcv4j_ffmpeg2_mediaType_SUBTITLE();
    public static final int AVMEDIA_TYPE_ATTACHMENT = FfmpegApi2.pcv4j_ffmpeg2_mediaType_ATTACHMENT();
    public static final int AVMEDIA_TYPE_NB = FfmpegApi2.pcv4j_ffmpeg2_mediaType_NB();

    static {
        // find the level
        final int logLevelSet;
        if(LOGGER.isTraceEnabled())
            logLevelSet = LOG_LEVEL_TRACE;
        else if(LOGGER.isDebugEnabled())
            logLevelSet = LOG_LEVEL_DEBUG;
        else if(LOGGER.isInfoEnabled())
            logLevelSet = LOG_LEVEL_INFO;
        else if(LOGGER.isWarnEnabled())
            logLevelSet = LOG_LEVEL_WARN;
        else if(LOGGER.isErrorEnabled())
            logLevelSet = LOG_LEVEL_ERROR;
        else
            logLevelSet = LOG_LEVEL_FATAL;

        FfmpegApi2.pcv4j_ffmpeg2_logging_setLogLevel(logLevelSet);
    }

    /**
     * The default here should match the DEFAULT_MAX_REMUX_ERRORS in ffmpeg_wrapper.cpp
     */
    public static final int DEFAULT_MAX_REMUX_ERRORS = 20;

    public static final String DEFAULT_CHAIN_NAME = "default";

    // ======================================================================
    // MEDIA DATA SOURCE SUPPORT
    // ======================================================================

    // ======================================================================
    // Custom media data source support

    @FunctionalInterface
    public static interface MediaDataSupplier {
        public int fillBuffer(ByteBuffer buf, int numBytes);
    }

    @FunctionalInterface
    public static interface MediaDataSeek {
        public long seekBuffer(long offset, int whence);
    }

    // ======================================================================

    // ======================================================================
    // MEDIA PROCESSING SUPPORT
    // ======================================================================

    /**
     * This interface is used for processors that handle decoded video frames.
     */
    @FunctionalInterface
    public static interface VideoFrameConsumer {
        public void handle(VideoFrame frame);
    }

    /**
     * This interface is used for defining stream selectors in java
     */
    @FunctionalInterface
    public static interface RawStreamSelectorCallback {
        public boolean select(boolean[] selection);
    }

    @FunctionalInterface
    public static interface StreamSelectorCallback {
        public boolean select(StreamDetails[] details, boolean[] selection);
    }

    public static class MediaProcessingChain extends MediaProcessor {

        private StreamSelector selector = null;
        private final StreamContext ctx;
        private final List<MediaProcessor> processors = new ArrayList<>();
        private final String name;

        private MediaProcessingChain(final String name, final long nativeRef, final StreamContext ctx) {
            super(nativeRef);
            this.ctx = ctx;
            this.name = name;
        }

        public StreamContext streamContext() {
            return ctx;
        }

        @Override
        public void close() {

            Functional.reverseRange(0, processors.size())
                .mapToObj(i -> processors.get(i))
                .forEach(p -> p.close());

            if(selector != null)
                selector.close();

        }

        // ======================================================================
        // STREAM SELECTOR SUPPORT
        // ======================================================================

        public MediaProcessingChain createFirstVideoStreamSelector() {
            return manage(new StreamSelector(FfmpegApi2.pcv4j_ffmpeg2_firstVideoStreamSelector_create()));
        }

        /**
         * This is package protected to eliminate any optimization of the strong references
         * required to keep the JNA callbacks from being GCed
         */
        static class CallbackStreamSelector extends StreamSelector {
            // ======================================================================
            // JNA will only hold a weak reference to the callbacks passed in
            // so if we dynamically allocate them then they will be garbage collected.
            // In order to prevent that we're keeping strong references to them.
            // These are not private in order to avoid any possibility that the
            // JVM optimized them out since they aren't read anywhere in this code.
            public select_streams_callback ssc;
            // ======================================================================

            private CallbackStreamSelector(final long nativeRef, final select_streams_callback selector) {
                super(nativeRef);
                ssc = selector;
            }

        }

        public MediaProcessingChain createStreamSelector(final StreamSelectorCallback callback) {
            return createStreamSelector(res -> {
                final StreamDetails[] sd = ctx.getStreamDetails();

                if(sd == null)
                    return false;

                LOGGER.debug("Selecting streams from {}", Arrays.toString(sd));

                if(sd.length != res.length) {
                    LOGGER.error(
                        "The number of stream determined from getStreamDetails ({}) is not equal to the number of streams determined by the result length ({})",
                        sd.length, res.length);
                    return false;
                }

                return callback.select(sd, res);
            });
        }

        public MediaProcessingChain createStreamSelector(final RawStreamSelectorCallback callback) {
            final var ssc = new select_streams_callback() {

                @Override
                public int select_streams(final int numStreams, final Pointer selected) {
                    final IntBuffer buf = selected.getByteBuffer(0, Integer.BYTES * numStreams).asIntBuffer();

                    final boolean[] res = new boolean[numStreams];
                    for(int i = 0; i < numStreams; i++)
                        res[i] = buf.get(i) == 0 ? false : true;

                    if(!callback.select(res))
                        return 0;

                    for(int i = 0; i < numStreams; i++)
                        buf.put(i, res[i] ? 1 : 0);

                    return 1;
                }

            };

            return manage(new CallbackStreamSelector(FfmpegApi2.pcv4j_ffmpeg2_javaStreamSelector_create(ssc), ssc));
        }

        /**
         * Create a video processor that takes the first decodable video stream.
         */
        public MediaProcessingChain createVideoFrameProcessor(final VideoFrameConsumer consumer) {
            final var pfc = new push_frame_callback() {

                final AtomicLong frameNumber = new AtomicLong(0);

                @Override
                public long push_frame(final long frame, final int isRbg, final int streamIndex) {
                    try(final VideoFrame mat = new VideoFrame(
                        frame, System.currentTimeMillis(), frameNumber.getAndIncrement(), isRbg == 0 ? false : true) {

                        // mats are closed automatically in the native code
                        // once the push_frame returns.
                        @Override
                        public void doNativeDelete() {}
                    };) {
                        try {
                            consumer.handle(mat);
                            return 0;
                        } catch(final FfmpegException ffe) {
                            long status = ffe.status;
                            if(status == 0)
                                status = AVERROR_UNKNOWN;
                            LOGGER.error("Pushing the frame failed in ffmpeg: {}", errorMessage(status), ffe);
                            return status;
                        } catch(final RuntimeException rte) {
                            final long status = AVERROR_UNKNOWN;
                            LOGGER.error("Pushing the frame failed in ffmpeg: {}", errorMessage(status), rte);
                            return status;
                        }
                    }
                }
            };

            final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_decodedFrameProcessor_create(pfc);
            return manage(new FrameVideoProcessor(nativeRef, pfc));
        }

        public MediaProcessingChain createUriRemuxer(final String fmt, final String outputUri, final int maxRemuxErrorCount) {
            return manage(new MediaProcessor(FfmpegApi2.pcv4j_ffmpeg2_uriRemuxer_create(fmt, outputUri, maxRemuxErrorCount)));
        }

        public MediaProcessingChain createUriRemuxer(final String outputUri, final int maxRemuxErrorCount) {
            return createUriRemuxer(null, outputUri, maxRemuxErrorCount);
        }

        public MediaProcessingChain createUriRemuxer(final String fmt, final String outputUri) {
            return createUriRemuxer(fmt, outputUri, DEFAULT_MAX_REMUX_ERRORS);
        }

        public MediaProcessingChain createUriRemuxer(final String outputUri) {
            return createUriRemuxer(null, outputUri, DEFAULT_MAX_REMUX_ERRORS);
        }

        public MediaProcessingChain optionally(final boolean doIt, final Consumer<MediaProcessingChain> ctxWork) {
            if(doIt)
                ctxWork.accept(this);
            return this;
        }

        private MediaProcessingChain manage(final MediaProcessor newProc) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_mediaProcessorChain_addProcessor(this.nativeRef, newProc.nativeRef));
            processors.add(newProc);
            return this;
        }

        private MediaProcessingChain manage(final StreamSelector selector) {
            if(this.selector != null)
                throw new FfmpegException("Selector for this processing chain is already set.");
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_mediaProcessorChain_setStreamSelector(this.nativeRef, selector.nativeRef));
            this.selector = selector;
            return this;
        }

    }

    /**
     * This is the base class opaque handle to an underlying native class
     * that processes packets.
     */
    private static class MediaProcessor implements QuietCloseable {
        protected final long nativeRef;

        private MediaProcessor(final long nativeRef) {
            this.nativeRef = nativeRef;
        }

        @Override
        public void close() {
            if(nativeRef != 0L)
                FfmpegApi2.pcv4j_ffmpeg2_mediaProcessor_destroy(nativeRef);
        }
    }

    private static class StreamSelector implements QuietCloseable {
        final long nativeRef;

        private StreamSelector(final long nativeRef) {
            this.nativeRef = nativeRef;
        }

        @Override
        public void close() {
            if(nativeRef != 0)
                FfmpegApi2.pcv4j_ffmpeg2_streamSelector_destroy(nativeRef);
        }
    }

    /**
     * This is a base class for a media processor that handles decoded video frames.
     *
     * This is package protected to eliminate any optimization of the strong references
     * required to keep the JNA callbacks from being GCed
     */
    static class FrameVideoProcessor extends MediaProcessor {
        // ======================================================================
        // JNA will only hold a weak reference to the callbacks passed in
        // so if we dynamically allocate them then they will be garbage collected.
        // In order to prevent that we're keeping strong references to them.
        // These are not private in order to avoid any possibility that the
        // JVM optimized them out since they aren't read anywhere in this code.
        public push_frame_callback pfc;
        // ======================================================================

        private FrameVideoProcessor(final long nativeRef, final push_frame_callback consumer) {
            super(nativeRef);
            pfc = consumer;
        }
    }

    public static StreamContext createStreamContext() {
        final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_streamContext_create();
        return new StreamContext(nativeRef);
    }

    public static class StreamContext implements QuietCloseable {
        private final long nativeRef;

        private MediaDataSource dataSource = null;
        private final List<MediaProcessingChain> mediaProcesingChains = new ArrayList<>();
        private final Map<String, MediaProcessingChain> mediaProcesingChainsMap = new HashMap<>();

        private StreamContext(final long nativeRef) {
            if(nativeRef == 0)
                throw new FfmpegException("Couldn't create new stream context");

            this.nativeRef = nativeRef;
        }

        public static class StreamDetails {
            public final int streamIndex;
            public final int mediaType;

            public final int fps_num;
            public final int fps_den;

            public final int tb_num;
            public final int tb_den;

            public final int codecId;
            public final String codecName;

            private StreamDetails(final FfmpegApi2.internal_StreamDetails sd) {
                streamIndex = sd.stream_index;
                mediaType = sd.mediaType;
                fps_num = sd.fps_num;
                fps_den = sd.fps_den;
                tb_num = sd.tb_num;
                tb_den = sd.tb_den;
                codecId = sd.codec_id;
                codecName = sd.codecName;
            }

            @Override
            public String toString() {
                return "StreamDetails [streamIndex=" + streamIndex + ", mediaType=" + mediaType + ", fps_num=" + fps_num + ", fps_den=" + fps_den + ", tb_num="
                    + tb_num + ", tb_den=" + tb_den + ", codecId=" + codecId + ", codecName=" + codecName + "]";
            }
        }

        public StreamDetails[] getStreamDetails() {
            final IntByReference numStreamsRef = new IntByReference();
            final LongByReference rc = new LongByReference();
            final FfmpegApi2.internal_StreamDetails.ByReference detailsRef = FfmpegApi2.pcv4j_ffmpeg2_streamContext_getStreamDetails(nativeRef, numStreamsRef,
                rc);
            try {
                throwIfNecessary(rc.getValue());

                final int numStreams = numStreamsRef.getValue();
                final FfmpegApi2.internal_StreamDetails[] details = numStreams == 0 ? new FfmpegApi2.internal_StreamDetails[0]
                    : (FfmpegApi2.internal_StreamDetails[])detailsRef.toArray(numStreams);

                return Arrays.stream(details)
                    .map(sd -> new StreamDetails(sd))
                    .toArray(StreamDetails[]::new);
            } finally {
                FfmpegApi2.pcv4j_ffmpeg2_streamDetails_deleteArray(detailsRef.getPointer());
            }
        }

        public StreamContext load() {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_streamContext_load(nativeRef), Ffmpeg2.AVERROR_EOF_KOGSTAT);
            return this;
        }

        public StreamContext play() {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_streamContext_play(nativeRef), Ffmpeg2.AVERROR_EOF_KOGSTAT);
            return this;
        }

        public StreamContext addOption(final String key, final String value) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_streamContext_addOption(nativeRef, key, value));
            return this;
        }

        public StreamContext addOptions(final Map<String, String> options) {
            options.entrySet().stream().forEach(e -> addOption(e.getKey(), e.getValue()));
            return this;
        }

        public synchronized StreamContext stop() {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_streamContext_stop(nativeRef));
            return this;
        }

        public synchronized StreamContext sync() {
            FfmpegApi2.pcv4j_ffmpeg2_streamContext_sync(nativeRef);
            return this;
        }

        public StreamContext optionally(final boolean doIt, final Consumer<StreamContext> ctxWork) {
            if(doIt)
                ctxWork.accept(this);
            return this;
        }

        public StreamContext peek(final Consumer<StreamContext> ctxWork) {
            ctxWork.accept(this);
            return this;
        }

        public synchronized int currentState() {
            return FfmpegApi2.pcv4j_ffmpeg2_streamContext_state(nativeRef);
        }

        @Override
        public void close() {
            if(nativeRef != 0) {
                if(currentState() == FfmpegApi2.STREAM_CONTEXT_STATE_PLAYING) {
                    stop();

                    final long endTime = System.currentTimeMillis() + 10000; // give it 10 seconds to stop
                    while(currentState() != FfmpegApi2.STREAM_CONTEXT_STATE_ENDED && (System.currentTimeMillis() < endTime))
                        Thread.yield();

                    if(currentState() != FfmpegApi2.STREAM_CONTEXT_STATE_ENDED)
                        LOGGER.warn("Couldn't stop the playing stream.");
                }
                FfmpegApi2.pcv4j_ffmpeg2_streamContext_delete(nativeRef);
            }

            if(dataSource != null)
                dataSource.close();

            Functional.reverseRange(0, mediaProcesingChains.size())
                .mapToObj(i -> mediaProcesingChains.get(i))
                .forEach(p -> p.close());

            mediaProcesingChains.clear();
        }

        // ======================================================================
        // MEDIA DATA SOURCE SUPPORT
        // ======================================================================

        /**
         * Create a raw data source from a URI. This sources media data from whatever the
         * URI is pointing to given it's supported by ffmpeg.
         *
         * @throws URISyntaxException
         */
        public StreamContext createMediaDataSource(final String url) throws URISyntaxException {
            return createMediaDataSource(new URI(url));
        }

        /**
         * Create a raw data source from a URI. This sources media data from whatever the
         * URI is pointing to given it's supported by ffmpeg.
         */
        public StreamContext createMediaDataSource(final URI url) {
            final String uriStr;

            // dewindowsfy the URI.
            if("file".equals(url.getScheme())) {
                // we need to fix the path if there's a windows disk in the uri.
                String tmp = url.toString();
                if(tmp.startsWith("file:"))
                    tmp = tmp.substring("file:".length());
                while(tmp.startsWith("/"))
                    tmp = tmp.substring(1);
                if(tmp.charAt(1) == ':')
                    uriStr = "file:" + tmp;
                else
                    uriStr = url.toString();
            } else
                uriStr = url.toString();

            final long nativeVds = FfmpegApi2.pcv4j_ffmpeg2_uriMediaDataSource_create(uriStr);
            if(nativeVds == 0)
                throw new FfmpegException("Failed to create a uri based native MediaDataSource");

            return manage(new MediaDataSource(nativeVds));
        }

        /**
         * Create a raw data source based on FFmpeg customIO. This is the same as calling
         * createMediaDataSource(vds, null);
         */
        public StreamContext createMediaDataSource(final MediaDataSupplier vds) {
            return createMediaDataSource(vds, null);
        }

        /**
         * Create a raw data source based on FFmpeg customIO. The job of the data supplier is to fill
         * the buffer with at most the number of bytes and return the number of bytes it was able to
         * put in the buffer. When the MediaDataSeek is not null, it's used to move the stream to the
         * desired location in the stream.
         */
        public StreamContext createMediaDataSource(final MediaDataSupplier dataSupplier, final MediaDataSeek seek) {

            final var ret = new CustomMediaDataSource(FfmpegApi2.pcv4j_ffmpeg2_customMediaDataSource_create());

            final ByteBuffer buffer = ret.customStreamBuffer();
            final int bufSize = ret.bufSize; // set after customStreamBuffer is called.

            ret.set(new fill_buffer_callback() {

                @Override
                public int fill_buffer(final int numBytesRequested) {
                    final int numBytes = Math.min(numBytesRequested, bufSize);
                    buffer.rewind();
                    return dataSupplier.fillBuffer(buffer, numBytes);
                }
            },
                seek != null ? new seek_buffer_callback() {
                    @Override
                    public long seek_buffer(final long offset, final int whence) {
                        return seek.seekBuffer(offset, whence);
                    }
                } : null);

            return manage(ret);
        }

        // ======================================================================
        // MEDIA PROCESSING SUPPORT
        // ======================================================================

        public MediaProcessingChain openChain(final String chainName) {
            final MediaProcessingChain cur = mediaProcesingChainsMap.get(chainName);
            if(cur != null)
                return cur;
            final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_mediaProcessorChain_create();
            if(nativeRef == 0)
                throw new FfmpegException("Failed to create a media processing chain");

            return manage(new MediaProcessingChain(chainName, nativeRef, this));
        }

        public MediaProcessingChain openChain() {
            return openChain(DEFAULT_CHAIN_NAME);
        }

        private StreamContext manage(final MediaDataSource vds) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_streamContext_setSource(nativeRef, vds.nativeRef));
            dataSource = vds;
            return this;
        }

        private MediaProcessingChain manage(final MediaProcessingChain vds) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_streamContext_addProcessor(nativeRef, vds.nativeRef));
            mediaProcesingChains.add(vds);
            mediaProcesingChainsMap.put(vds.name, vds);
            return vds;
        }
    }

    // ======================================================================
    // ENCODER
    // ======================================================================

    public static EncodingContext createEncoder() {
        final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_encodingContext_create();
        return new EncodingContext(nativeRef);
    }

    public static class EncodingContext implements QuietCloseable {

        public class VideoEncoder implements QuietCloseable {
            private final long nativeRef;

            private VideoEncoder(final long nativeRef) {
                this.nativeRef = nativeRef;
            }

            public VideoEncoder addCodecOptions(final String key, final String values) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_addCodecOption(nativeRef, key, values));
                return this;
            }

            public VideoEncoder setEncodingParameters(final int pfps, final int pbufferSize, final long pminBitrate, final long pmaxBitrate) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_setEncodingParameters(nativeRef, pfps, pbufferSize, pminBitrate, pmaxBitrate));
                return this;
            }

            public VideoEncoder setFps(final int pfps) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_setFps(nativeRef, pfps));
                return this;
            }

            public VideoEncoder setBufferSize(final int pbufferSize) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_setBufferSize(nativeRef, pbufferSize));
                return this;
            }

            public VideoEncoder setBitrate(final long pminBitrate, final long pmaxBitrate) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_setBitrate(nativeRef, pminBitrate, pmaxBitrate));
                return this;
            }

            public VideoEncoder setBitrate(final long pminBitrate) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_setBitrate2(nativeRef, pminBitrate));
                return this;
            }

            public EncodingContext enable(final Mat frame, final boolean isRgb) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable(nativeRef, frame.nativeObj, isRgb ? 1 : 0));
                return EncodingContext.this;
            }

            public EncodingContext enable(final boolean isRgb, final int width, final int height, final int stride) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable2(nativeRef, isRgb ? 1 : 0, width, height, stride));
                return EncodingContext.this;
            }

            public EncodingContext enable(final boolean isRgb, final int width, final int height) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable3(nativeRef, isRgb ? 1 : 0, width, height));
                return EncodingContext.this;
            }

            public void encode(final Mat frame, final boolean isRgb) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_encode(nativeRef, frame.nativeObj, isRgb ? 1 : 0));
            }

            public void stop() {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_stop(nativeRef));
            }

            public EncodingContext encodingContext() {
                return EncodingContext.this;
            }

            @Override
            public void close() {
                if(nativeRef != 0)
                    FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_delete(nativeRef);
            }
        }

        private final long nativeRef;
        private final LinkedList<QuietCloseable> toClose = new LinkedList<>();
        private final Map<String, VideoEncoder> encoders = new HashMap<>();

        private EncodingContext(final long nativeRef) {
            this.nativeRef = nativeRef;
        }

        public EncodingContext outputStream(final String fmt, final String outputUri) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_encodingContext_setOutput(nativeRef, fmt, outputUri));
            return this;
        }

        public EncodingContext outputStream(final String outputUri) {
            return outputStream(null, outputUri);
        }

        public VideoEncoder openVideoEncoder(final String codec, final String name) {
            if(encoders.containsKey(name))
                throw new FfmpegException("Cannot add a second encoder with the name \"" + name + "\"");
            final var ret = new VideoEncoder(FfmpegApi2.pcv4j_ffmpeg2_encodingContext_openVideoEncoder(nativeRef, codec));
            toClose.addFirst(ret);
            encoders.put(name, ret);
            return ret;
        }

        public VideoEncoder openVideoEncoder(final String codec) {
            return openVideoEncoder(codec, codec);
        }

        public VideoEncoder getVideoEncoder(final String encoderName) {
            return encoders.get(encoderName);
        }

        public EncodingContext ready() {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_encodingContext_ready(nativeRef));
            return this;
        }

        public EncodingContext stop() {
            encoders.values().forEach(v -> v.stop());
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_encodingContext_stop(nativeRef));
            return this;
        }

        @Override
        public void close() {
            ignore(() -> stop(), re -> LOGGER.error("Failed on stopping the EncodingContext", re));

            toClose.forEach(q -> q.close());

            if(nativeRef != 0)
                FfmpegApi2.pcv4j_ffmpeg2_encodingContext_delete(nativeRef);
        }
    }

    private static class MediaDataSource implements QuietCloseable {
        protected final long nativeRef;

        private MediaDataSource(final long nativeRef) {
            this.nativeRef = nativeRef;
        }

        @Override
        public void close() {
            if(nativeRef != 0L)
                FfmpegApi2.pcv4j_ffmpeg2_mediaDataSource_destroy(nativeRef);
        }
    }

    /**
     * This is package protected to eliminate any optimization of the strong references
     * required to keep the JNA callbacks from being GCed
     */
    static class CustomMediaDataSource extends MediaDataSource {
        // ======================================================================
        // JNA will only hold a weak reference to the callbacks passed in
        // so if we dynamically allocate them then they will be garbage collected.
        // In order to prevent that we're keeping strong references to them.
        // These are not private in order to avoid any possibility that the
        // JVM optimized them out since they aren't read anywhere in this code.
        public fill_buffer_callback strongRefDs;
        public seek_buffer_callback strongRefS;
        // ======================================================================

        int bufSize = -1;

        public CustomMediaDataSource(final long nativeRef) {
            super(nativeRef);
        }

        private void set(final fill_buffer_callback fill, final seek_buffer_callback seek) {
            strongRefDs = fill;
            strongRefS = seek;
            FfmpegApi2.pcv4j_ffmpeg2_customMediaDataSource_set(nativeRef, fill, seek);
        }

        private ByteBuffer customStreamBuffer() {
            final Pointer value = FfmpegApi2.pcv4j_ffmpeg2_customMediaDataSource_buffer(nativeRef);
            bufSize = FfmpegApi2.pcv4j_ffmpeg2_customMediaDataSource_bufferSize(nativeRef);
            return value.getByteBuffer(0, bufSize);
        }
    }

    private static void throwIfNecessary(final long status, final long... ignore) {
        final Set<Long> toIgnore = Arrays.stream(ignore).mapToObj(Long::valueOf).collect(Collectors.toSet());
        if(status != 0L && !toIgnore.contains(status)) {
            throw new FfmpegException(status, errorMessage(status));
        }
    }

    private static String errorMessage(final long errorCode) {
        final MutableObject<Pointer> nmes = new MutableObject<>(null);
        try(final QuietCloseable qc = () -> FfmpegApi2.pcv4j_ffmpeg2_utils_freeString(nmes.getValue());) {
            nmes.setValue(FfmpegApi2.pcv4j_ffmpeg2_utils_statusMessage(errorCode));
            return Optional.ofNullable(nmes.getValue()).orElseThrow(() -> new FfmpegException("Failed to retrieve status message for code: " + errorCode))
                .getString(0);
        }
    }

}
