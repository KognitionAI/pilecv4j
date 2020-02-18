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

    static {
        // read a properties file from the classpath.
        final Properties ocvVersionProps = new Properties();
        try (InputStream ocvVerIs = ImageAPI.class.getClassLoader().getResourceAsStream(OCV_VERSION_PROPS)) {
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
            .library("opencv_java" + ocvShortVersion)
            .library(LIBNAME)
            .addCallback((dir, libname, oslibname) -> {
                if(LIBNAME.equals(libname))
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();

        Native.register(LIBNAME);
    }

    public static native long CvRaster_copy(long nativeMatHandle);

    public static native long CvRaster_move(long nativeMatHandle);

    public static native void CvRaster_assign(long nativeHandleDest, long nativeMatHandleSrc);

    public static native Pointer CvRaster_getData(long nativeMatHandle);

    public static native long CvRaster_makeMatFromRawDataReference(int rows, int cols, int type, long dataLong);

    public static native long CvRaster_defaultMat();

    // ==========================================================
    // Wrapped OpenCv HighGUI API.
    // ALL of these need to be called from a SINGLE common thread.
    public static native void CvRaster_showImage(String name, long nativeMatHandle);

    public static native void CvRaster_updateWindow(String name, long nativeMatHandle);

    public static native int CvRaster_fetchEvent(int millisToSleep);

    public static native void CvRaster_destroyWindow(String name);

    public static native boolean CvRaster_isWindowClosed(String name);
    // ==========================================================

    // =========================================================
    // MJPEGWriter functionality
    // =========================================================
    public static native boolean mjpeg_initializeMJPEG(String filename);

    public static native boolean mjpeg_doappendFile(String filename, int width, int height);

    public static native boolean mjpeg_close(int fps);

    public static native void mjpeg_cleanUp();
    // =========================================================

    public interface AddHoughSpaceEntryContributorFunc extends Callback {
        public boolean add(int orow, int ocol, int hsr, int hsc, int hscount);
    }

    // =========================================================
    // Hough Transform functionality
    // =========================================================
    public static native void Transform_houghTransformNative(final long image, final int width, final int height, final long gradientDirImage,
        final byte[] mask, final int maskw, final int maskh, final int maskcr, final int maskcc,
        final byte[] gradientDirMask, final int gdmaskw, final int gdmaskh, final int gdmaskcr, final int gdmaskcc,
        final double gradientDirSlopDeg, final double quantFactor, short[] ret, int hswidth, int hsheight,
        AddHoughSpaceEntryContributorFunc hsem, int houghThreshold, int rowstart, int rowend, int colstart, int colend,
        byte EDGE);
    // =========================================================

    // public static native long GpuMat_create(long nativeObject);
    //
    // public static native void GpuMat_destroy(long nativeObject);
    //
    // public static native long Gpu_createSparsePyrLKOpticalFlowEngine();
    //
    // public static native void Gpu_destroySparsePyrLKOpticalFlowEngine(long ptrL);
    //
    // public static native long Gpu_createGoodFeaturesToTrackDetector(int srcType, int maxCorners, double qualityLevel, double minDistance, int blockSize,
    // boolean useHarrisDetector, double harrisK);
    //
    // public static native void Gpu_destroyGoodFeaturesToTrackDetector(long ptrL);
    //
    // public static native void Gpu_GoodFeaturesToTrackDetector_detect(long detectorPtrL, long imageL, long cornersL, long maskL);

}
