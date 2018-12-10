package ai.kognition.pilecv4j.image;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

import ai.kognition.pilecv4j.util.HackLoadLibrary;

public class CvRasterAPI {
//   private static final Logger LOGGER = LoggerFactory.getLogger(CvRasterAPI.class);
//
//   public static final String OCV_VERSION_PROPS = "opencv-info.version";
//   public static final String OCV_SHORT_VERSION_PROP_NAME = "opencv-short.version";
//   public static final String LIBNAME = "ai.kognition.pilecv4j";

   static void _init() {}

   static {
//      // read a properties file from the classpath.
//      final Properties ocvVersionProps = new Properties();
//      try (InputStream ocvVerIs = CvRasterAPI.class.getClassLoader().getResourceAsStream(OCV_VERSION_PROPS)) {
//         ocvVersionProps.load(ocvVerIs);
//      } catch(final IOException e) {
//         throw new IllegalStateException("Problem loading the properties file \"" + OCV_VERSION_PROPS + "\" from the classpath", e);
//      }
//
//      final String ocvShortVersion = ocvVersionProps.getProperty(OCV_SHORT_VERSION_PROP_NAME);
//      if(ocvShortVersion == null)
//         throw new IllegalStateException("Problem reading the short version from the properties file \"" + OCV_VERSION_PROPS + "\" from the classpath");
//
//      LOGGER.debug("Loading the library for opencv with a short version {}", ocvShortVersion);
//
//      NativeLibraryLoader.loader()
//            .optional("opencv_ffmpeg" + ocvShortVersion + "_64")
//            .library("opencv_java" + ocvShortVersion)
//            .library(LIBNAME)
//            .addCallback((dir, libname, oslibname) -> {
//               if(LIBNAME.equals(libname))
//                  NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
//            })
//            .load();

	  HackLoadLibrary.init();
      Native.register(HackLoadLibrary.LIBNAME);
   }

   public static native long CvRaster_copy(long nativeMatHandle);

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

}
