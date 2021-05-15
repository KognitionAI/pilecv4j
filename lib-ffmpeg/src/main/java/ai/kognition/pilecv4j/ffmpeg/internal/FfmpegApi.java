package ai.kognition.pilecv4j.ffmpeg.internal;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

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

    static {
        if(inited.get())
            throw new IllegalStateException("Cannot initialize the Ffmpeg twice.");

        CvMat.initOpenCv();

        if(!inited.getAndSet(true)) {
            NativeLibraryLoader.loader()
                .library(LIBNAME)
                .destinationDir(new File(System.getProperty("java.io.tmpdir"), LIBNAME).getAbsolutePath())
                .addCallback((dir, libname, oslibname) -> {
                    LOGGER.info("scanning dir:{}, libname:{}, oslibname:{}", dir, libname, oslibname);
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                })
                .load();

            Native.register(LIBNAME);
        }

        pcv4j_ffmpeg_set_im_maker(ImageAPI.get_im_maker());
    }

    public static interface push_frame_callback extends Callback {
        public void push_frame(long val, int isRbg);
    }

    public static void _init() {}

    public static native int pcv4j_ffmpeg_init();

    public static native Pointer pcv4j_ffmpeg_statusMessage(long status);

    public static native void pcv4j_ffmpeg_freeString(Pointer str);

    // ===================================================================
    // Stream functions
    // ===================================================================
    /**
     * Returns a stream context reference.
     */
    public static native long pcv4j_ffmpeg_createContext();

    /**
     * Delete a previously created stream context returned from createStreamContext
     */
    public static native void pcv4j_ffmpeg_deleteContext(long streamCtx);

    /**
     * Prepare a stream context for reading from the given url
     *
     * @return status
     */
    public static native long pcv4j_ffmpeg_openStream(long streamCtx, String url);

    /**
     * Once a stream is open, find the first video stream within the container
     *
     * @return status
     */
    public static native long pcv4j_ffmpeg_findFirstVideoStream(long streamCtx);

    /**
     * Process the frames with the callback until the stream ends or until/unless
     * there is a problem.
     *
     * @return status
     */
    public native static long pcv4j_ffmpeg_process_frames(long streamCtx, push_frame_callback func);

    /**
     * set the log level for the Ffmpeg_wrapper.
     *
     * @return status
     */
    public native static long pcv4j_ffmpeg_set_log_level(long streamCtx, int logLevel);

    /**
     * Set an option for the ffmpeg call.
     *
     * @return status
     */
    public native static long pcv4j_ffmpeg_add_option(long streamCtx, String key, String value);

    /**
     * Set synchronize the stream with real time. Should only be used with files.
     */
    public native static void pcv4j_ffmpeg_set_syc(final long nativeDef, final int doIt);

    public native static long pcv4j_ffmpeg_stop(final long nativeDef);
    // ===================================================================

    public native static void pcv4j_ffmpeg_set_im_maker(long immakerRef);

}
