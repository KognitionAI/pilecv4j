package com.jiminger.image;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.opencv.core.Mat;

public class CvMat extends org.opencv.core.Mat implements AutoCloseable {
   private static final CvRasterAPI API = CvRasterAPI.API;

   private static final Method nDelete;
   private static final Method nZeros;
   private static final Method nOnes;
   private static final Field nativeObjField;

   private boolean deletedAlready = false;

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

   // called from CvRaster
   CvMat(final long nativeObj) {
      super(nativeObj);
   }

   public CvMat() {}

   public CvMat(final int rows, final int cols, final int type) {
      super(rows, cols, type);
   }

   public CvMat(final int rows, final int cols, final int type, final ByteBuffer data) {
      super(rows, cols, type, data);
   }

   public static CvMat move(final Mat mat) {
      final long defaultMatNativeObj = API.CvRaster_defaultMat();
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
   protected void finalize() throws Throwable {}

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
