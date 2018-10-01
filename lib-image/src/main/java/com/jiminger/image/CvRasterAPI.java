package com.jiminger.image;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import net.dempsy.util.library.NativeLibraryLoader;

public class CvRasterAPI {
   public static final String OPENCV_SHORT_VERSION = "343";

   public static final String LIBNAME = "utilities.jiminger.com";

   static void _init() {}

   static {
      NativeLibraryLoader.loader()
            .optional("opencv_ffmpeg" + OPENCV_SHORT_VERSION + "_64")
            .library("opencv_java" + OPENCV_SHORT_VERSION)
            .library(LIBNAME)
            .addCallback((dir, libname, oslibname) -> {
               if(LIBNAME.equals(libname))
                  NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();

      Native.register(LIBNAME);
   }

   public static native long CvRaster_copy(long nativeMatHandle);

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
   // ==========================================================
}
