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
package ai.kognition.pilecv4j.image;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public class ImageAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageAPI.class);

    public static final String OCV_VERSION_PROPS = "opencv-info.version";
    public static final String OCV_SHORT_VERSION_PROP_NAME = "opencv-short.version";
    public static final String LIBNAME = "ai.kognition.pilecv4j.image";

    static void _init() {}

    // needs to match LogLevel enum in the C++ code.
    public static final int LOG_LEVEL_TRACE = 0;
    public static final int LOG_LEVEL_DEBUG = 1;
    public static final int LOG_LEVEL_INFO = 2;
    public static final int LOG_LEVEL_WARN = 3;
    public static final int LOG_LEVEL_ERROR = 4;
    public static final int LOG_LEVEL_FATAL = 5;

    static {
        // read a properties file from the classpath.
        final Properties ocvVersionProps = new Properties();
        try(InputStream ocvVerIs = ImageAPI.class.getClassLoader().getResourceAsStream(OCV_VERSION_PROPS)) {
            ocvVersionProps.load(ocvVerIs);
        } catch(final IOException e) {
            throw new IllegalStateException("Problem loading the properties file \"" + OCV_VERSION_PROPS + "\" from the classpath", e);
        }

        final String ocvShortVersion = ocvVersionProps.getProperty(OCV_SHORT_VERSION_PROP_NAME);
        if(ocvShortVersion == null)
            throw new IllegalStateException("Problem reading the short version from the properties file \"" +
                OCV_VERSION_PROPS + "\" from the classpath");

        LOGGER.debug("Loading the library for opencv with a short version {}", ocvShortVersion);

        NativeLibraryLoader.loader()
            .optional("opencv_ffmpeg" + ocvShortVersion + "_64")
            .optional("opencv_videoio_ffmpeg" + ocvShortVersion + "_64")
            .library("opencv_java" + ocvShortVersion)
            .library(LIBNAME)
            .addPreLoadCallback((dir, libname, oslibname) -> {
                if(LIBNAME.equals(libname))
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();

        Native.register(LIBNAME);
    }

    public static native long pilecv4j_image_CvRaster_copy(long nativeMatHandle);

    public static native long pilecv4j_image_CvRaster_move(long nativeMatHandle);

    public static native void pilecv4j_image_CvRaster_freeByMove(long nativeMatHandle);

    public static native void pilecv4j_image_CvRaster_assign(long nativeHandleDest, long nativeMatHandleSrc);

    public static native Pointer pilecv4j_image_CvRaster_getData(long nativeMatHandle);

    public static native long pilecv4j_image_CvRaster_makeMatFromRawDataReference(int rows, int cols, int type, long dataLong);

    // "sizes" is a literal uint32_t* to match java
    public static native long pilecv4j_image_CvRaster_makeMdMatFromRawDataReference(int ndims, Pointer sizes, int type, long dataLong);

    public static native void pilecv4j_image_CvRaster_inplaceReshape(long nativeRef, int cn, int ndims, Pointer sizes);

    public static native int pilecv4j_image_CvRaster_inplaceRemake(long nativeObj, int ndims, Pointer ptr, int type, long maxSize);

    public static native void pilecv4j_image_CvRaster_zeroCopyDecode(long srcBuffer, int flags, long dstMatRef);

    // ==========================================================
    // Wrapped OpenCv HighGUI API.
    // ALL of these need to be called from a SINGLE common thread.
    public static native void pilecv4j_image_CvRaster_showImage(String name, long nativeMatHandle);

    public static native void pilecv4j_image_CvRaster_updateWindow(String name, long nativeMatHandle);

    public static native int pilecv4j_image_CvRaster_fetchEvent(int millisToSleep);

    public static native void pilecv4j_image_CvRaster_destroyWindow(String name);

    public static native boolean pilecv4j_image_CvRaster_isWindowClosed(String name);
    // ==========================================================

    // =========================================================
    // MJPEGWriter functionality
    // =========================================================
    public static native int pilecv4j_image_mjpeg_initializeMJPEG(String filename);

    public static native int pilecv4j_image_mjpeg_doappendFile(String filename, int width, int height);

    public static native int pilecv4j_image_mjpeg_close(int fps);

    public static native void pilecv4j_image_mjpeg_cleanUp();
    // =========================================================

    public interface AddHoughSpaceEntryContributorFunc extends Callback {
        public boolean add(int orow, int ocol, int hsr, int hsc, int hscount);
    }

    // =========================================================
    // Hough Transform functionality
    // =========================================================
    public static native void pilecv4j_image_Transform_houghTransformNative(final long image, final int width, final int height, final long gradientDirImage,
        final byte[] mask, final int maskw, final int maskh, final int maskcr, final int maskcc,
        final byte[] gradientDirMask, final int gdmaskw, final int gdmaskh, final int gdmaskcr, final int gdmaskcc,
        final double gradientDirSlopDeg, final double quantFactor, short[] ret, int hswidth, int hsheight,
        AddHoughSpaceEntryContributorFunc hsem, int houghThreshold, int rowstart, int rowend, int colstart, int colend,
        byte EDGE);
    // =========================================================

    // =========================================================
    // Gst bridge functionality
    // =========================================================

    public native static long pilecv4j_image_get_im_maker();

    // =========================================================

    public native static void pilecv4j_image_setLogLevel(int logLevel);

}
