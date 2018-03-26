package com.jiminger.image;

import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

import net.dempsy.util.library.NativeLibraryLoader;

public interface ImageAPI extends Library {

    public static final String LIBNAME = "utilities.jiminger.com";

    static final AtomicBoolean inited = new AtomicBoolean(false);

    public static String _init() {
        if (!inited.getAndSet(true)) {
            NativeLibraryLoader.loader()
                    .library(LIBNAME)
                    .addCallback((dir, libname, oslibname) -> {
                        NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                    })
                    .load();
        }
        return LIBNAME;
    }

    static final ImageAPI API = Native.loadLibrary(_init(), ImageAPI.class);

    // =========================================================
    // MJPEGWriter functionality
    // =========================================================
    public boolean mjpeg_initializeMJPEG(String filename);

    public boolean mjpeg_doappendFile(String filename, int width, int height);

    public boolean mjpeg_close(int fps);

    public void mjpeg_cleanUp();
    // =========================================================

    public interface AddHoughSpaceEntryContributorFunc extends Callback {
        public boolean add(int orow, int ocol, int hsr, int hsc, int hscount);
    }

    // =========================================================
    // Hough Transform functionality
    // =========================================================
    void Transform_houghTransformNative(final long image, final int width, final int height, final long gradientDirImage,
            final byte[] mask, final int maskw, final int maskh, final int maskcr, final int maskcc,
            final byte[] gradientDirMask, final int gdmaskw, final int gdmaskh, final int gdmaskcr, final int gdmaskcc,
            final double gradientDirSlopDeg, final double quantFactor, short[] ret, int hswidth, int hsheight,
            AddHoughSpaceEntryContributorFunc hsem, int houghThreshold, int rowstart, int rowend, int colstart, int colend,
            byte EDGE);
    // =========================================================

}
