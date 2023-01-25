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

package ai.kognition.pilecv4j.ffmpeg;

import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_DEBUG;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_ERROR;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_FATAL;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_INFO;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_TRACE;
import static ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.LOG_LEVEL_WARN;
import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.ignore;

import java.net.URI;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
import net.dempsy.util.MutableRef;
import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.StreamContext.StreamDetails;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.fill_buffer_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.packet_filter_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.push_frame_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.seek_buffer_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.select_streams_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi2.write_buffer_callback;
import ai.kognition.pilecv4j.image.CvMat;
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

    // This needs to be kept in sync with the value in EncodingContext.h
    public static final int DEFAULT_FPS = 30;

    public static final long DEFAULT_MAX_LATENCY_MILLIS = 500;

    static {
        final Logger nativeLogger = LoggerFactory.getLogger(Ffmpeg2.class.getPackageName() + ".native");

        // find the level
        final int logLevelSet;
        if(nativeLogger.isTraceEnabled())
            logLevelSet = LOG_LEVEL_TRACE;
        else if(nativeLogger.isDebugEnabled())
            logLevelSet = LOG_LEVEL_DEBUG;
        else if(nativeLogger.isInfoEnabled())
            logLevelSet = LOG_LEVEL_INFO;
        else if(nativeLogger.isWarnEnabled())
            logLevelSet = LOG_LEVEL_WARN;
        else if(nativeLogger.isErrorEnabled())
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
     * This interface is used for remuxing (TODO: or encoding) to java memory
     */
    @FunctionalInterface
    public static interface WritePacket {
        public void handle(ByteBuffer packet, int len);
    }

    /**
     * You can implement a stream selector in java by passing a StreamSelectorCallback
     * to {@link MediaProcessingChain#createStreamSelector(StreamSelectorCallback)}.
     */
    @FunctionalInterface
    public static interface StreamSelectorCallback {
        /**
         * The details for all of the streams will be passed to you and you need to fill out
         * the selection array. {@code true} means you want the stream data passed to
         * the processing.
         *
         * @return {@code true} on success. {@code false} on failure.
         */
        public boolean select(StreamDetails[] details, boolean[] selection);
    }

    public static interface PacketFilterCallback {
        /**
         * @return given the details, should this packet be let through
         */
        public boolean filter(final int mediaType, final int stream_index, final int packetNumBytes, final boolean isKeyFrame, final long pts, final long dts,
            final int tbNum, final int tbDen);
    }

    /**
     * Calling {@link Ffmpeg2.StreamContext#openChain(String)} returns a {@link Ffmpeg2.MediaProcessingChain}
     * which represents a selection of streams and a number of processes that operate on those
     * streams.
     */
    public static class MediaProcessingChain extends MediaProcessor {

        private StreamSelector selector = null;
        private final StreamContext ctx;
        private final List<MediaProcessor> processors = new ArrayList<>();
        private final List<PacketFilter> packetFilters = new ArrayList<>();
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

            Functional.reverseRange(0, packetFilters.size())
                .mapToObj(i -> packetFilters.get(i))
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

        /**
         * This is package protected to eliminate any optimization of the strong references
         * required to keep the JNA callbacks from being GCed
         */
        static class CallbackPacketFilter extends PacketFilter {
            // ======================================================================
            // JNA will only hold a weak reference to the callbacks passed in
            // so if we dynamically allocate them then they will be garbage collected.
            // In order to prevent that we're keeping strong references to them.
            // These are not private in order to avoid any possibility that the
            // JVM optimized them out since they aren't read anywhere in this code.
            public packet_filter_callback pfcb;
            // ======================================================================

            private CallbackPacketFilter(final long nativeRef, final packet_filter_callback selector) {
                super(nativeRef);
                pfcb = selector;
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

        private push_frame_callback wrap(final VideoFrameConsumer consumer) {
            return new push_frame_callback() {

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
        }

        /**
         * Create a video processor that takes the first decodable video stream.
         */
        public MediaProcessingChain createVideoFrameProcessor(final VideoFrameConsumer consumer) {
            return createVideoFrameProcessor((String)null, consumer);
        }

        /**
         * Create a video processor that takes the first decodable video stream and applies the initializer on the
         * first frame and the handler on all of the other frames.
         */
        public MediaProcessingChain createVideoFrameProcessor(final VideoFrameConsumer initializer, final VideoFrameConsumer handler) {
            return createVideoFrameProcessor(null, initializer, handler);
        }

        /**
         * Create a video processor that takes the first decodable video stream. If decoderName is not null then the decoder
         * will be used to decode the frames.
         */
        public MediaProcessingChain createVideoFrameProcessor(final String decoderName, final VideoFrameConsumer consumer) {
            final var pfc = wrap(consumer);

            final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_decodedFrameProcessor_create(pfc, decoderName);
            return manage(new FrameVideoProcessor(nativeRef, pfc));
        }

        /**
         * Create a video processor that takes the first decodable video stream and applies the initializer on the
         * first frame and the handler on all of the other frames. If decoderName is not null then the decoder
         * will be used to decode the frames.
         */
        public MediaProcessingChain createVideoFrameProcessor(final String decoderName, final VideoFrameConsumer initializer,
            final VideoFrameConsumer handler) {
            final var pfc = wrap(handler);

            final MutableRef<FrameVideoProcessor> proc = new MutableRef<>();
            final var init = wrap(vf -> {
                initializer.handle(vf);
                proc.ref.replace(pfc);
                handler.handle(vf);
            });

            final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_decodedFrameProcessor_create(init, decoderName);
            final var fm = new FrameVideoProcessor(nativeRef, init);
            proc.ref = fm;
            return manage(fm);
        }

        public MediaProcessingChain createRemuxer(final String fmt, final String outputUri, final int maxRemuxErrorCount) {
            final MediaOutput output = new MediaOutput(FfmpegApi2.pcv4j_ffmpeg2_uriOutput_create(fmt, outputUri));
            return manage(new MediaProcessorWithCustomOutput(FfmpegApi2.pcv4j_ffmpeg2_remuxer_create(maxRemuxErrorCount), output));
        }

        public MediaProcessingChain createRemuxer(final String outputUri, final int maxRemuxErrorCount) {
            return createRemuxer(null, outputUri, maxRemuxErrorCount);
        }

        public MediaProcessingChain createRemuxer(final String fmt, final String outputUri) {
            return createRemuxer(fmt, outputUri, DEFAULT_MAX_REMUX_ERRORS);
        }

        public MediaProcessingChain createRemuxer(final String outputUri) {
            return createRemuxer(null, outputUri, DEFAULT_MAX_REMUX_ERRORS);
        }

        private static class CustomOutput extends MediaOutput {
            // ======================================================================
            // JNA will only hold a weak reference to the callbacks passed in
            // so if we dynamically allocate them then they will be garbage collected.
            // In order to prevent that, we're keeping strong references to them.
            // These are not private in order to avoid any possibility that the
            // JVM optimized them out since they aren't read anywhere in this code.
            @SuppressWarnings("unused") public write_buffer_callback strongRefW = null;
            // ======================================================================

            private CustomOutput(final long nativeRef) {
                super(nativeRef);
            }

            private ByteBuffer customBuffer() {
                final Pointer value = FfmpegApi2.pcv4j_ffmpeg2_customOutput_buffer(nativeRef);
                final int bufSize = FfmpegApi2.pcv4j_ffmpeg2_customOutput_bufferSize(nativeRef);
                return value.getByteBuffer(0, bufSize);
            }

            private void set(final write_buffer_callback write) {
                strongRefW = write;
                FfmpegApi2.pcv4j_ffmpeg2_customOutput_set(nativeRef, write);
            }
        }

        public MediaProcessingChain createRemuxer(final String outputFormat, final WritePacket writer) {

            final var remuxerRef = FfmpegApi2.pcv4j_ffmpeg2_remuxer_create(DEFAULT_MAX_REMUX_ERRORS);
            final var output = new CustomOutput(FfmpegApi2.pcv4j_ffmpeg2_customOutput_create(outputFormat, null));

            final ByteBuffer bb = output.customBuffer();

            output.set(numBytes -> {
                writer.handle(bb, numBytes);
                return 0L;
            });

            return manage(new MediaProcessorWithCustomOutput(remuxerRef, output));
        }

        public MediaProcessingChain optionally(final boolean doIt, final Consumer<MediaProcessingChain> ctxWork) {
            if(doIt)
                ctxWork.accept(this);
            return this;
        }

        public MediaProcessingChain createPacketFilter(final PacketFilterCallback cb) {
            final packet_filter_callback rcb = new packet_filter_callback() {

                @Override
                public int packet_filter(final int mediaType, final int stream_index, final int packetNumBytes, final int isKeyFrame, final long pts,
                    final long dts, final int tbNum, final int tbDen) {
                    return cb.filter(mediaType, stream_index, packetNumBytes, isKeyFrame == 0 ? false : true, pts, dts, tbNum, tbDen) ? 1 : 0;
                }

            };

            return manage(new CallbackPacketFilter(FfmpegApi2.pcv4j_ffmpeg2_javaPacketFilter_create(rcb), rcb));
        }

        @FunctionalInterface
        private static interface RawStreamSelectorCallback {
            public boolean select(boolean[] selection);
        }

        private MediaProcessingChain createStreamSelector(final RawStreamSelectorCallback callback) {
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

        private MediaProcessingChain manage(final MediaProcessor newProc) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_mediaProcessorChain_addProcessor(this.nativeRef, newProc.nativeRef));
            processors.add(newProc);
            return this;
        }

        private MediaProcessingChain manage(final PacketFilter newProc) {
            throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_mediaProcessorChain_addPacketFilter(this.nativeRef, newProc.nativeRef));
            packetFilters.add(newProc);
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
        final long nativeRef;

        private MediaProcessor(final long nativeRef) {
            this.nativeRef = nativeRef;
        }

        @Override
        public void close() {
            if(nativeRef != 0L)
                FfmpegApi2.pcv4j_ffmpeg2_mediaProcessor_destroy(nativeRef);
        }
    }

    private static class MediaProcessorWithCustomOutput extends MediaProcessor {
        private final MediaOutput output;

        private MediaProcessorWithCustomOutput(final long nativeRef) {
            this(nativeRef, null);
        }

        private MediaProcessorWithCustomOutput(final long nativeRef, final MediaOutput output) {
            super(nativeRef);
            this.output = output;
            FfmpegApi2.pcv4j_ffmpeg2_remuxer_setOutput(nativeRef, output.nativeRef);
        }

        @Override
        public void close() {
            if(output != null)
                output.close();

            super.close();
        }
    }

    private static class MediaOutput implements QuietCloseable {
        final long nativeRef;

        private MediaOutput(final long nativeRef) {
            this.nativeRef = nativeRef;
        }

        @Override
        public void close() {
            if(nativeRef != 0L)
                FfmpegApi2.pcv4j_ffmpeg2_mediaOutput_delete(nativeRef);
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

    private static class PacketFilter implements QuietCloseable {
        final long nativeRef;

        private PacketFilter(final long nativeRef) {
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
    public static class FrameVideoProcessor extends MediaProcessor {
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

        public void replace(final push_frame_callback consumer) {
            pfc = consumer;
            FfmpegApi2.pcv4j_ffmpeg2_decodedFrameProcessor_replace(super.nativeRef, consumer);
        }
    }

    public static StreamContext createStreamContext() {
        final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_streamContext_create();
        return new StreamContext(nativeRef);
    }

    /**
     * <p>
     * A {@link StreamContext} represents the coupling of an input source to a set of
     * processing on the media streams in that source. It's also a builder for declaring
     * the media source and that processing to be done.
     * </p>
     * <p align="center">
     * Fig 1.
     * </p>
     * <p align="center">
     * <img src="https://raw.githubusercontent.com/KognitionAI/pilecv4j/master/docs/Stream%20Context.png" width="500">
     * </p>
     *
     * <p>
     * <ul>
     * <li><b>MediaDataSource:</b> The MediaDataSource is responsible for connecting to
     * a source of media data. There are two main types of MediaDataSource.
     * <ul>
     * <li>The first is a simple URI based input which is instantiated when you use
     * {@link #createMediaDataSource(String)}. This is the same as the {@code -i} option
     * passed to the {@code ffmpeg} command line.</li>
     * <li>The second is a custom data source where you can supply raw media stream data
     * dynamically by supplying a {@link MediaDataSupplier} callback implementation and
     * optionally a {@link MediaDataSeek} implementation. These will be called by the
     * system in order to fetch more data or, when a {@link MediaDataSeek} is supplied,
     * move around in the stream.</li>
     * </ul>
     * </li>
     * <li><b>MediaProcessingChain:</b> Data packets from the MediaDataSource are passed
     * to a series of {@link MediaProcessingChain}s. {@link MediaProcessingChain}s are added
     * to a {@link StreamContext} using the {@link #openChain(String)} call.
     * {@link MediaProcessingChain}s couple a means of selecting which media streams from
     * the MediaDataSource are to be processed with the series of processing.
     * <ul>
     * <li><em>StreamSelector:</em> A {@link StreamSelector} sets up a simple filter that will only
     * allow packets from the selected streams through to be processed by the {@link MediaProcessor}s.
     * A {@link StreamSelector} is added to a {@link MediaProcessingChain} by calling one of the
     * {@code create*StreamSelector(...)} methods.</li>
     * <li><em>MediaProcessor:</em> A set of {@link MediaProcessor}s can then process the packets
     * that make it through the selection filter. There are currently two main processors but this
     * will likely grow in the future:
     * <ul>
     * <li>A uri remuxer: This will allow the packets to be remuxed and output to a given URI.
     * You add a remuxer using the call {@link MediaProcessingChain#createRemuxer(String)}</li>
     * <li>A frame processor: The packets will be fully decoded and the frames will be passed
     * to the callback provided. A frame processor can be added by calling
     * {@link MediaProcessingChain#createVideoFrameProcessor(VideoFrameConsumer)}</li>
     * </ul>
     * </li>
     * </ul>
     * </li>
     * </ul>
     * </p>
     * <p>
     * The processing can then be kicked off by calling {@link #play()} on the fully configured
     * {@link StreamContext}
     * </p>
     * <h3>Additional Information</h3>
     * <p>
     * A {@link StreamContext} goes through the following internal states:
     * <ul>
     * <li>FRESH - When a context is instantiated, it's in this state.</li>
     * <li>OPEN - The media data source is opened
     * (<a href="https://ffmpeg.org/doxygen/3.4/group__lavf__decoding.html#ga31d601155e9035d5b0e7efedc894ee49">avformat_open_input</a>).</li>
     * </ul>
     * </p>
     */
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
         * Create a data source from a URI or file name. This sources media data from whatever the
         * URI is pointing to given it's supported by ffmpeg.
         */
        public StreamContext createMediaDataSource(final String source) {
            final long nativeVds = FfmpegApi2.pcv4j_ffmpeg2_uriMediaDataSource_create(source);
            if(nativeVds == 0)
                throw new FfmpegException("Failed to create a uri based native MediaDataSource");

            return manage(new MediaDataSource(nativeVds));
        }

        /**
         * Create a data source from a URI. This sources media data from whatever the
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
         * <p>
         * Create a data source from a URI or file name and explicitly specify the format. This
         * sources media data from whatever the URI is pointing to with the explicit format
         * given it's supported by ffmpeg.
         * </p>
         *
         * <p>
         * This can be used to specify a device like a web cam. For example, on Linux you
         * can call {@code createMediaDataSource("video4linux2", "/dev/video0")}.
         * see {@linkplain https://trac.ffmpeg.org/wiki/Capture/Webcam} for more details
         * </p>
         *
         * @see https://trac.ffmpeg.org/wiki/Capture/Webcam
         */
        public StreamContext createMediaDataSource(final String fmt, final String rawFile) {
            final long nativeVds = FfmpegApi2.pcv4j_ffmpeg2_uriMediaDataSource_create2(fmt, rawFile);
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

        /**
         * Create if it doesn't exist, or return the existing, {@link MediaProcessingChain} with the given name.
         */
        public MediaProcessingChain openChain(final String chainName) {
            final MediaProcessingChain cur = mediaProcesingChainsMap.get(chainName);
            if(cur != null)
                return cur;
            final long nativeRef = FfmpegApi2.pcv4j_ffmpeg2_mediaProcessorChain_create();
            if(nativeRef == 0)
                throw new FfmpegException("Failed to create a media processing chain");

            return manage(new MediaProcessingChain(chainName, nativeRef, this));
        }

        /**
         * Because it's confusing as to whether or not you're always opening a new chain
         * when you call this, you should be explicit and use {@link #openChain(String)}
         *
         * @deprecated use {@link #openChain(String)}
         * @return if it doesn't already exist, a newly created {@link MediaProcessingChain}
         * with the name {@link Ffmpeg2#DEFAULT_CHAIN_NAME}. If it does exist it will
         * return that already created {@link MediaProcessingChain}
         */
        @Deprecated
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

    public static class StreamingEncoder implements QuietCloseable {
        private static final AtomicLong threadCount = new AtomicLong(0);

        private final VideoEncoder videoEncoder;
        private boolean deepCopy = false;

        private final AtomicBoolean stopMe = new AtomicBoolean(false);
        private final AtomicReference<Sample> onDeck = new AtomicReference<>(null);
        private Thread encoderThread = null;

        private final AtomicReference<RuntimeException> failure = new AtomicReference<>(null);

        private final static class Sample implements QuietCloseable {
            public final CvMat frame;
            public final boolean isRgb;

            public Sample(final CvMat frame, final boolean isRgb) {
                this.frame = frame;
                this.isRgb = isRgb;
            }

            @Override
            public void close() {
                frame.close();
            }
        }

        private StreamingEncoder(final VideoEncoder ctx) {
            this.videoEncoder = ctx;
        }

        public StreamingEncoder deepCopy(final boolean deepCopy) {
            this.deepCopy = deepCopy;
            return this;
        }

        public void encode(final Mat frame, final boolean isRgb) {

            final RuntimeException rte = failure.get();
            if(rte != null)
                throw new FfmpegException("Error from encoder thread", rte);

            if(frame != null) {
                try(CvMat copied = deepCopy ? CvMat.deepCopy(frame) : CvMat.shallowCopy(frame);) {
                    try(var sample = onDeck.getAndSet(new Sample(copied.returnMe(), isRgb));) {}
                }
            }

        }

        public EncodingContext encodingContext() {
            return videoEncoder.encodingContext();
        }

        @Override
        public void close() {
            stopMe.set(true);
            ignore(() -> encoderThread.join(5000));
            if(encoderThread.isAlive())
                LOGGER.warn("Failed to stop the encoder thread.");

            videoEncoder.close();
        }

        private void start() {

            encoderThread = chain(new Thread(() -> {
                Sample prev;
                // wait for at least one.
                {
                    Sample curSample;
                    do {
                        curSample = onDeck.getAndSet(null);
                        if(curSample == null)
                            Thread.yield();
                    } while(curSample == null && !stopMe.get());

                    prev = curSample;
                }

                while(!stopMe.get()) {

                    final Sample toEncode;
                    {
                        final Sample curSample = onDeck.getAndSet(null);
                        if(curSample != null) {
                            prev.close();
                            toEncode = prev = curSample;
                        } else
                            toEncode = prev;
                    }

                    try {
                        videoEncoder.encode(toEncode.frame, toEncode.isRgb);
                    } catch(final RuntimeException rte) {
                        failure.set(rte);
                        break;
                    }
                }

            }, "Encoding Thread " + threadCount.getAndIncrement()), t -> t.start());
        }

    }

    public static class EncodingContext implements QuietCloseable {

        public class VideoEncoder implements QuietCloseable {
            private final long nativeRef;
            boolean inputIsRgb = false;

            int fps = DEFAULT_FPS;
            boolean closed = false;
            boolean enabled = false;

            StreamingEncoder se = null;

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
                inputIsRgb = isRgb;
                enabled = true;
                if(se != null)
                    se.start();
                return EncodingContext.this;
            }

            public EncodingContext enable(final boolean isRgb, final int width, final int height, final int stride) {
                inputIsRgb = isRgb;
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable2(nativeRef, isRgb ? 1 : 0, width, height, stride));
                inputIsRgb = isRgb;
                enabled = true;
                if(se != null)
                    se.start();
                return EncodingContext.this;
            }

            public EncodingContext enable(final boolean isRgb, final int width, final int height) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable3(nativeRef, isRgb ? 1 : 0, width, height));
                inputIsRgb = isRgb;
                enabled = true;
                if(se != null)
                    se.start();
                return EncodingContext.this;
            }

            public EncodingContext enable(final Mat frame, final boolean isRgb, final int destWidth, final int destHeight) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable4(nativeRef, frame.nativeObj, isRgb ? 1 : 0, destWidth, destHeight));
                inputIsRgb = isRgb;
                enabled = true;
                if(se != null)
                    se.start();
                return EncodingContext.this;
            }

            public EncodingContext enable(final boolean isRgb, final int width, final int height, final int stride, final int destWidth, final int destHeight) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable5(nativeRef, isRgb ? 1 : 0, width, height, stride, destWidth, destHeight));
                inputIsRgb = isRgb;
                enabled = true;
                if(se != null)
                    se.start();
                return EncodingContext.this;
            }

            public EncodingContext enable(final boolean isRgb, final int width, final int height, final int destWidth, final int destHeight) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_enable6(nativeRef, isRgb ? 1 : 0, width, height, destWidth, destHeight));
                inputIsRgb = isRgb;
                enabled = true;
                if(se != null)
                    se.start();
                return EncodingContext.this;
            }

            public void encode(final Mat frame, final boolean isRgb) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_encode(nativeRef, frame.nativeObj, isRgb ? 1 : 0));
            }

            public void encode(final Mat frame) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_encode(nativeRef, frame.nativeObj, inputIsRgb ? 1 : 0));
            }

            public void stop() {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_stop(nativeRef));
            }

            public StreamingEncoder streamingEncoder(final long maxLatencyMillis) {
                throwIfNecessary(FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_streaming(nativeRef));
                final var r = new StreamingEncoder(this);
                if(enabled)
                    r.start();
                return r;
            }

            public StreamingEncoder streamingEncoder() {
                return streamingEncoder(DEFAULT_MAX_LATENCY_MILLIS);
            }

            public EncodingContext encodingContext() {
                return EncodingContext.this;
            }

            @Override
            public void close() {
                if(!closed && nativeRef != 0)
                    FfmpegApi2.pcv4j_ffmpeg2_videoEncoder_delete(nativeRef);
                closed = true;
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
