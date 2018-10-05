package com.jiminger.image;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CvMat extends org.opencv.core.Mat implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger(CvMat.class);
   private static boolean TRACK_MEMORY_LEAKS = false;

   static {
      CvRasterAPI._init();
   }

   // This is used when there's an input matrix that can't be null but should be ignored.
   public static final Mat nullMat = new Mat();

   private static final Method nDelete;
   private static final Method nZeros;
   private static final Method nOnes;
   private static final Field nativeObjField;

   private boolean deletedAlready = false;

   private final RuntimeException stackTrace;

   static {
      try {
         nDelete = org.opencv.core.Mat.class.getDeclaredMethod("n_delete", long.class);
         nDelete.setAccessible(true);

         nZeros = org.opencv.core.Mat.class.getDeclaredMethod("n_zeros", int.class, int.class, int.class);
         nZeros.setAccessible(true);

         nOnes = org.opencv.core.Mat.class.getDeclaredMethod("n_ones", int.class, int.class, int.class);
         nOnes.setAccessible(true);

         nativeObjField = org.opencv.core.Mat.class.getDeclaredField("nativeObj");
         nativeObjField.setAccessible(true);
      } catch(NoSuchMethodException | NoSuchFieldException | SecurityException e) {
         throw new RuntimeException(
               "Got an exception trying to access Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.",
               e);
      }
   }

   private CvMat(final long nativeObj) {
      super(nativeObj);
      stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException() : null;
   }

   // called from CvRaster
   public static CvMat wrapNative(final long nativeObj) {
      return new CvMat(nativeObj);
   }

   public CvMat() {
      stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException() : null;
   }

   public CvMat(final int rows, final int cols, final int type) {
      super(rows, cols, type);
      stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException() : null;
   }

   public CvMat(final int rows, final int cols, final int type, final ByteBuffer data) {
      super(rows, cols, type, data);
      stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException() : null;
   }

   /**
    * This performs a proper matrix multiplication that returns this * other.
    * The called should take control of the matrix returned.
    */
   public CvMat mm(final Mat other) {
      return mm(other, 1.0D);
   }

   public CvMat mm(final Mat other, final double scale) {
      final Mat ret = new Mat();
      Core.gemm(this, other, scale, nullMat, 0.0D, ret);
      return CvMat.move(ret);
   }

   public static CvMat shallowCopy(final Mat mat) {
      return new CvMat(CvRasterAPI.CvRaster_copy(mat.nativeObj));
   }

   public static CvMat deepCopy(final Mat mat) {
      final CvMat ret = new CvMat();
      mat.copyTo(ret);
      return ret;
   }

   public static CvMat move(final Mat mat) {
      final long defaultMatNativeObj = CvRasterAPI.CvRaster_defaultMat();
      try {
         final long nativeObjToUse = mat.nativeObj;
         nativeObjField.set(mat, defaultMatNativeObj);
         return new CvMat(nativeObjToUse);
      } catch(final IllegalAccessException e) {
         throw new RuntimeException(
               "Got an exception trying to set Mat.nativeObj. Either the security model is too restrictive or the version of OpenCv can't be supported.",
               e);
      }
   }

   public static CvMat zeros(final int rows, final int cols, final int type) {
      try {
         return new CvMat((Long)nZeros.invoke(CvMat.class, rows, cols, type));
      } catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
         throw new RuntimeException(
               "Got an exception trying to call Mat.n_zeros. Either the security model is too restrictive or the version of OpenCv can't be supported.",
               e);
      }
   }

   public static CvMat ones(final int rows, final int cols, final int type) {
      try {
         return new CvMat((Long)nOnes.invoke(CvMat.class, rows, cols, type));
      } catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
         throw new RuntimeException(
               "Got an exception trying to call Mat.n_ones. Either the security model is too restrictive or the version of OpenCv can't be supported.",
               e);
      }
   }

   // Prevent finalize from being called
   @Override
   protected void finalize() throws Throwable {
      if(!deletedAlready) {
         LOGGER.debug("Finalizing a {} that hasn't been closed.", CvMat.class.getSimpleName());
         if(TRACK_MEMORY_LEAKS)
            LOGGER.debug("Here's where I was instantiated: ", stackTrace);
         close();
      }
   }

   @Override
   public void close() {
      if(!deletedAlready) {
         try {
            nDelete.invoke(this, super.nativeObj);
         } catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(
                  "Got an exception trying to call Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.",
                  e);
         }
         deletedAlready = true;
      }
   }

   @Override
   public String toString() {
      return "CvMat: (" + getClass().getName() + "@" + Integer.toHexString(hashCode()) + ") " + super.toString();
   }

}
