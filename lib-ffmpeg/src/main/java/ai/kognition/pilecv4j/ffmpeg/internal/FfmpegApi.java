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

        set_im_maker(ImageAPI.get_im_maker());
    }

    public static interface push_frame_callback extends Callback {
        public void push_frame(long val, int isRbg);
    }

    public static void _init() {}

    public static native int ffmpeg_init();

    public static native Pointer ffmpeg_statusMessage(long status);

    public static native void ffmpeg_freeString(Pointer str);

    // ===================================================================
    // Stream functions
    // ===================================================================
    /**
     * Returns a stream context reference.
     */
    public static native long ffmpeg_createContext();

    /**
     * Delete a previously created stream context returned from createStreamContext
     */
    public static native void ffmpeg_deleteContext(long streamCtx);

    /**
     * Prepare a stream context for reading from the given url
     */
    public static native long ffmpeg_openStream(long streamCtx, String url);

    /**
     * Once a stream is open, find the first video stream within the container
     */
    public static native long ffmpeg_findFirstVideoStream(long streamCtx);

    public native static long process_frames(long streamCtx, push_frame_callback func);
    // ===================================================================

    public native static void set_im_maker(long immakerRef);

    public native static long enable_logging(int doIt);

}
