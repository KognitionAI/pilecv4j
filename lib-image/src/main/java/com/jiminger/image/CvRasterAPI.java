package com.jiminger.image;

import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import net.dempsy.util.library.NativeLibraryLoader;

public interface CvRasterAPI extends Library {
   public static final String LIBNAME = "utilities.jiminger.com";

   static final AtomicBoolean inited = new AtomicBoolean(false);

   public static CvRasterAPI _init() {
      if(!inited.getAndSet(true)) {
         NativeLibraryLoader.loader()
               .optional("opencv_ffmpeg342_64")
               .library("opencv_java342")
               .library("utilities.jiminger.com")
               .addCallback((dir, libname, oslibname) -> {
                  if(LIBNAME.equals(libname))
                     NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
               })
               .load();
      }

      return Native.loadLibrary(LIBNAME, CvRasterAPI.class);
   }

   static final CvRasterAPI API = _init();

   public long CvRaster_copy(long nativeMatHandle);

   public Pointer CvRaster_getData(long nativeMatHandle);

   public long CvRaster_makeMatFromRawDataReference(int rows, int cols, int type, long dataLong);

   public long CvRaster_defaultMat();

   public interface _KeyPressCallback extends Callback {
      public boolean keyPressed(int keyPressed);
   }

   public void CvRaster_showImage(String name, long nativeMatHandle, _KeyPressCallback callback, int waitMillis);

   public void CvRaster_updateWindow(String name, long nativeMatHandle);
}
