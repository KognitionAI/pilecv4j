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

package ai.kognition.pilecv4j.ffmpeg.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;
import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public class FfmpegApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegApi.class);

    public static final AtomicBoolean inited = new AtomicBoolean(false);
    public static final String LIBNAME = "ai.kognition.pilecv4j.ffmpeg";

    // needs to match LogLevel enum in the C++ code.
    public static final int LOG_LEVEL_TRACE = 0;
    public static final int LOG_LEVEL_DEBUG = 1;
    public static final int LOG_LEVEL_INFO = 2;
    public static final int LOG_LEVEL_WARN = 3;
    public static final int LOG_LEVEL_ERROR = 4;
    public static final int LOG_LEVEL_FATAL = 5;

    // needs to match the StreamContext state enum in the C++ code.
    public static final int STREAM_CONTEXT_STATE_FRESH = 0;
    public static final int STREAM_CONTEXT_STATE_OPEN = 1;
    public static final int STREAM_CONTEXT_STATE_LOADED = 2;
    public static final int STREAM_CONTEXT_STATE_PROCESSORS_SETUP = 3;
    public static final int STREAM_CONTEXT_STATE_PLAYING = 4;
    public static final int STREAM_CONTEXT_STATE_STOPPING = 5;
    public static final int STREAM_CONTEXT_STATE_ENDED = 6;

    static {
        if(inited.get())
            throw new IllegalStateException("Cannot initialize the Ffmpeg twice.");

        CvMat.initOpenCv();

        if(!inited.getAndSet(true)) {
            NativeLibraryLoader.loader()
                .library(LIBNAME)
                .destinationDir(new File(System.getProperty("java.io.tmpdir"), LIBNAME).getAbsolutePath())
                .addPreLoadCallback((dir, libname, oslibname) -> {
                    LOGGER.info("scanning dir:{}, libname:{}, oslibname:{}", dir, libname, oslibname);
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                })
                .load();

            Native.register(LIBNAME);

            pcv4j_ffmpeg2_imageMaker_set(ImageAPI.pilecv4j_image_get_im_maker());
        }
    }

    // called from Ffmpeg2 to load the class
    public static void _init() {}

    // ==========================================================
    // Custom IO callback declarations
    // ==========================================================
    public static interface fill_buffer_callback extends Callback {
        public int fill_buffer(final int numBytesToWrite);
    }

    public static interface seek_buffer_callback extends Callback {
        public long seek_buffer(final long offset, final int whence);
    }

    public static interface write_buffer_callback extends Callback {
        public long write_buffer(final int numBytesToWrite);
    }

    // ==========================================================
    // frame processing callback declarations
    // ==========================================================
    public static interface push_frame_callback extends Callback {
        public long push_frame(final long val, final int isRbg, final int streamIndex);
    }

    // ==========================================================
    // Stream selector callback declarations
    // ==========================================================
    public static interface select_streams_callback extends Callback {
        public int select_streams(final int numStreams, Pointer selected);
    }

    public static interface packet_filter_callback extends Callback {
        public int packet_filter(final int mediaType, final int stream_index, final int packetNumBytes, final int isKeyFrame, final long pts, final long dts,
            final int tbNum, final int tbDen);
    }

    // ==========================================================
    // Segmented Muxer callback declarations
    // ==========================================================
    public static interface create_muxer_from_java_callback extends Callback {
        public long next_muxer(final long muxerNumber, LongByReference muxerOut);
    }

    public static interface should_close_segment_callback extends Callback {
        public int should_close_segment(int mediaType, int stream_index, int packetNumBytes,
            int isKeyFrame, long pts, long dts, int tbNum, int tbDen);
    }

    // ==========================================================
    // Utilities
    // ==========================================================
    public static native void pcv4j_ffmpeg2_logging_setLogLevel(final int logLevel);

    public static native Pointer pcv4j_ffmpeg2_utils_statusMessage(final long status);

    public static native void pcv4j_ffmpeg2_utils_freeString(final Pointer str);

    public static native void pcv4j_ffmpeg2_imageMaker_set(final long im);

    // ==========================================================
    // Stream Context construction/destruction
    // ==========================================================
    public static native long pcv4j_ffmpeg2_mediaContext_create();

    public static native void pcv4j_ffmpeg2_mediaContext_delete(final long nativeRef);

    public static class internal_StreamDetails extends Structure {
        public int stream_index;
        public int mediaType;

        public int fps_num;
        public int fps_den;

        public int tb_num;
        public int tb_den;

        public int codec_id;
        public String codecName;

        public static class ByReference extends internal_StreamDetails implements Structure.ByReference {}

        private static final List<String> fo = gfo(internal_StreamDetails.class, "stream_index", "mediaType", "fps_num", "fps_den", "tb_num", "tb_den",
            "codec_id", "codecName");

        public internal_StreamDetails() {}

        public internal_StreamDetails(final Pointer ptr) {
            super(ptr);
        }

        @Override
        protected List<String> getFieldOrder() {
            return fo;
        }

        @Override
        public String toString() {
            return "internal_StreamDetails [mediaType=" + mediaType + ", fps_num=" + fps_num + ", fps_den=" + fps_den + ", tb_num=" + tb_num + ", tb_den="
                + tb_den + ", codecName=" + codecName + "]";
        }
    }

    public static native internal_StreamDetails.ByReference pcv4j_ffmpeg2_mediaContext_getStreamDetails(final long ctx, final IntByReference numResults,
        LongByReference rc);

    public static native void pcv4j_ffmpeg2_streamDetails_deleteArray(Pointer p);

    // ==========================================================
    // MediaDataSource lifecycle methods
    // ==========================================================

    public static native void pcv4j_ffmpeg2_mediaDataSource_destroy(final long vdsRef);

    /**
     * Get a Uri based MediaDataSource source.
     *
     * @return a reference to a native MediaDataSource built from a source uri.
     */
    public static native long pcv4j_ffmpeg2_uriMediaDataSource_create(final String sourceUri);

    public static native long pcv4j_ffmpeg2_uriMediaDataSource_create2(final String fmt, final String source);

    public static native long pcv4j_ffmpeg2_customMediaDataSource_create();

    public static native long pcv4j_ffmpeg2_customMediaDataSource_set(final long nativeRef, final fill_buffer_callback vds, seek_buffer_callback seek);

    /**
     * When running a custom data source, a constant ByteBuffer wrapping native memory
     * is used to transfer the data. The size of that buffer is retrieved with this call.
     */
    public static native int pcv4j_ffmpeg2_customMediaDataSource_bufferSize(final long vdsRef);

    /**
     * When running a custom data source, a constant ByteBuffer wrapping native memory
     * is used to transfer the data. That buffer is retrieved using this call.
     */
    public static native Pointer pcv4j_ffmpeg2_customMediaDataSource_buffer(final long vdsRef);

    // ==========================================================
    // MediaProcessor lifecycle methods
    // ==========================================================

    public static native void pcv4j_ffmpeg2_mediaProcessor_destroy(final long vdsRef);

    public static native long pcv4j_ffmpeg2_decodedFrameProcessor_create(final push_frame_callback cb, final String decoderName);

    public static native void pcv4j_ffmpeg2_decodedFrameProcessor_replace(final long nativeRef, final push_frame_callback cb);

    public static native long pcv4j_ffmpeg2_remuxer_create(long outputRef, final int maxRemuxErrorCount);

    // ==========================================================
    // Muxers methods
    // ==========================================================

    public static native void pcv4j_ffmpeg2_muxer_delete(final long outputRef);

    public static native long pcv4j_ffmpeg2_defaultMuxer_create(final String pfmt, final String poutputUri, final write_buffer_callback callback,
        seek_buffer_callback seek);

    public static native Pointer pcv4j_ffmpeg2_defaultMuxer_buffer(final long ctx);

    public static native int pcv4j_ffmpeg2_defaultMuxer_bufferSize(final long ctx);

    public static native long pcv4j_ffmpeg2_segmentedMuxer_create(final create_muxer_from_java_callback create_muxer_callback,
        final should_close_segment_callback ssc_callback);

    // ==========================================================
    // MediaProcessorChain methods
    // ==========================================================

    public static native long pcv4j_ffmpeg2_mediaProcessorChain_create();

    public static native void pcv4j_ffmpeg2_mediaProcessorChain_destroy(long nativeRef);

    public static native long pcv4j_ffmpeg2_mediaProcessorChain_addProcessor(long mpc, long mp);

    public static native long pcv4j_ffmpeg2_mediaProcessorChain_addPacketFilter(long mpc, long pf);

    // ==========================================================
    // Filters and Stream selectors
    // ==========================================================

    public static native long pcv4j_ffmpeg2_firstVideoStreamSelector_create();

    public static native long pcv4j_ffmpeg2_javaStreamSelector_create(select_streams_callback callback);

    public static native long pcv4j_ffmpeg2_javaPacketFilter_create(packet_filter_callback callback);

    public static native void pcv4j_ffmpeg2_packetFilter_destroy(long nativeRef);

    // ==========================================================
    // Stream Context setup methods
    // ==========================================================

    public static native long pcv4j_ffmpeg2_mediaContext_setSource(final long ctxRef, final long mediaDataSourceRef);

    public static native long pcv4j_ffmpeg2_mediaContext_addProcessor(final long ctxRef, final long mediaProcessorRef);

    /**
     * Set an option for the ffmpeg call (e.g. rtsp_transport = tcp).
     */
    public native static long pcv4j_ffmpeg2_mediaContext_addOption(final long streamCtx, final String key, final String value);

    /**
     * Play the stream and carry out all of the processing that should have been
     * set up prior to calling this method.
     */
    public static native long pcv4j_ffmpeg2_mediaContext_play(final long ctx);

    /**
     * Stop a playing stream. If the stream isn't in the PLAY state, then it will return an error.
     * If the stream is already in a STOP state, this will do nothing and return no error.
     */
    public native static long pcv4j_ffmpeg2_mediaContext_stop(final long nativeDef);

    public native static int pcv4j_ffmpeg2_mediaContext_state(final long nativeDef);

    public native static void pcv4j_ffmpeg2_mediaContext_sync(final long nativeDef);

    // ==========================================================
    // Encoding
    // ==========================================================

    public native static long pcv4j_ffmpeg2_encodingContext_create();

    public native static void pcv4j_ffmpeg2_encodingContext_delete(final long nativeDef);

    public native static long pcv4j_ffmpeg2_encodingContext_setMuxer(final long nativeDef, long muxerRef);

    public native static long pcv4j_ffmpeg2_encodingContext_openVideoEncoder(final long encCtxRef, final String video_codec);

    public native static long pcv4j_ffmpeg2_encodingContext_ready(final long encCtxRef);

    public native static long pcv4j_ffmpeg2_encodingContext_stop(final long nativeDef);

    public native static long pcv4j_ffmpeg2_videoEncoder_addCodecOption(final long nativeDef, final String key, final String val);

    public native static long pcv4j_ffmpeg2_videoEncoder_enable(final long nativeDef, final int isRgb, final int width, final int height, final int stride,
        final int dstW, final int dstH);

    public native static void pcv4j_ffmpeg2_videoEncoder_delete(final long nativeDef);

    public native static long pcv4j_ffmpeg2_videoEncoder_encode(final long nativeDef, final long matRef, final int isRgb);

    public native static long pcv4j_ffmpeg2_videoEncoder_setFramerate(final long nativeDef, final int pfps_num, final int pfps_den);

    public native static long pcv4j_ffmpeg2_videoEncoder_setOutputDims(final long nativeDef, final int width, final int height, int preserveAspectRatio,
        int onlyScaleDown);

    public native static long pcv4j_ffmpeg2_videoEncoder_setRcBufferSize(final long nativeDef, final int pbufferSize);

    public native static long pcv4j_ffmpeg2_videoEncoder_setRcBitrate(final long nativeDef, final long pminBitrate, final long pmaxBitrate);

    public native static long pcv4j_ffmpeg2_videoEncoder_setTargetBitrate(final long nativeDef, final long pbitrate);

    public native static long pcv4j_ffmpeg2_videoEncoder_stop(final long nativeDef);

    public native static long pcv4j_ffmpeg2_videoEncoder_streaming(final long nativeDef);

    // ==========================================================
    // Error codes
    // ==========================================================
    /**
     * Get the AV Error code for EOF. Can be called at any time.
     */
    public static native int pcv4j_ffmpeg_code_averror_eof();

    public static native long pcv4j_ffmpeg_code_averror_eof_as_kognition_stat();

    public static native long pcv4j_ffmpeg_code_averror_unknown_as_kognition_stat();

    /**
     * Get the seek code.
     */
    public static native int pcv4j_ffmpeg_code_seek_set();

    /**
     * Get the seek code.
     */
    public static native int pcv4j_ffmpeg_code_seek_cur();

    /**
     * Get the seek code.
     */
    public static native int pcv4j_ffmpeg_code_seek_end();

    /**
     * Get the "error: code for EAGAIN
     */
    public static native int pcv4j_ffmpeg_code_eagain();

    /**
     * Get the FFmpeg specific seek code. This means just return
     * the entire stream size or a negative number if not supported.
     */
    public static native int pcv4j_ffmpeg_code_seek_size();

    public static native int pcv4j_ffmpeg2_mediaType_UNKNOWN();

    public static native int pcv4j_ffmpeg2_mediaType_VIDEO();

    public static native int pcv4j_ffmpeg2_mediaType_AUDIO();

    public static native int pcv4j_ffmpeg2_mediaType_DATA();

    public static native int pcv4j_ffmpeg2_mediaType_SUBTITLE();

    public static native int pcv4j_ffmpeg2_mediaType_ATTACHMENT();

    public static native int pcv4j_ffmpeg2_mediaType_NB();

    public static native void pcv4j_ffmpeg2_timings();

    private static List<String> gfo(final Class<?> clazz, final String... fieldNames) {
        try {
            final ArrayList<String> ret = new ArrayList<>(fieldNames.length);
            for(final String fn: fieldNames)
                ret.add(clazz.getField(fn)
                    .getName());
            return ret;
        } catch(final NoSuchFieldException | SecurityException e) {
            // This will only happen if the structure changes and should cause systemic
            // test failures pointing to that fact.
            throw new RuntimeException(e);
        }
    }

    public static interface get_frame_callback extends Callback {
        public long get_frame();
    }
}
