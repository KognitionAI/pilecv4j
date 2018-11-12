package ai.kognition.pilecv4j.image;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class is an easier (perhaps) and more efficient interface to an OpenCV Mat.
 * Than the one available through the Java wrapper. It includes more efficient resource
 * management as an {@link AutoCloseable}.
 * </p>
 */
public class CvMat extends Mat implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger(CvMat.class);
   private static boolean TRACK_MEMORY_LEAKS = false;
   private boolean skipCloseOnceForReturn = false;

   static {
      CvRasterAPI._init();
      ImageAPI._init();
   }

   public static void initOpenCv() {}

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

   public <T> T rasterOp(final Function<CvRaster, T> function) {
      try (final CvRaster raster = CvRaster.makeInstance(this)) {
         return function.apply(raster);
      }
   }

   public void rasterAp(final Consumer<CvRaster> function) {
      try (final CvRaster raster = CvRaster.makeInstance(this)) {
         function.accept(raster);
      }
   }

   public long numBytes() {
      return elemSize() * rows() * cols();
   }

   @Override
   public void close() {
      if(!skipCloseOnceForReturn) {
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
      } else
         skipCloseOnceForReturn = false; // next close counts.
   }

   @Override
   public String toString() {
      return "CvMat: (" + getClass().getName() + "@" + Integer.toHexString(hashCode()) + ") " + super.toString();
   }

   public static <T> T rasterOp(final Mat mat, final Function<CvRaster, T> function) {
      if(mat instanceof CvMat)
         return ((CvMat)mat).rasterOp(function);
      else {
         try (CvRaster raster = CvRaster.makeInstance(mat)) {
            return function.apply(raster);
         }
      }
   }

   public static void rasterAp(final Mat mat, final Consumer<CvRaster> function) {
      if(mat instanceof CvMat)
         ((CvMat)mat).rasterAp(function);
      else {
         try (CvRaster raster = CvRaster.makeInstance(mat)) {
            function.accept(raster);
         }
      }
   }

   /**
    * This call should be made to manage a copy of the Mat using a {@link CvRaster}.
    * NOTE!! Changes to the {@link CvMat} will be reflected in the {@link Mat} and
    * vs. vrs. If you want a deep copy/clone of the original Mat then consider
    * using {@link CvMat#deepCopy(Mat)}.
    */
   public static CvMat shallowCopy(final Mat mat) {
      return new CvMat(CvRasterAPI.CvRaster_copy(mat.nativeObj));
   }

   /**
    * This call will manage a complete deep copy of the provided {@code Mat}.
    * Changes in one will not be reflected in the other.
    */
   public static CvMat deepCopy(final Mat mat) {
      if(mat.rows() == 0)
         return move(new Mat(mat.rows(), mat.cols(), mat.type()));
      if(mat.isContinuous())
         return move(mat.clone());

      final CvMat newMat = new CvMat(mat.rows(), mat.cols(), mat.type());
      mat.copyTo(newMat);
      return newMat;
   }

   /**
    * This call should be made to hand management of the Mat over to the {@link CvMat}.
    * The Mat shouldn't be used after this
    * call or, at least, it shouldn't be assumed to still be pointing to the same image
    * data. When the CvRaster is closed, it will release the data that was originally
    * associated with the {@code Mat}. If you want to keep the {@code Mat} beyond the
    * life of the {@link CvMat} returnec, then consider using {@link CvMat#shallowCopy(Mat)}.
    */
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

   /**
    * This implements {@code leftOp = rightOp}
    */
   public static void reassign(final Mat leftOp, final Mat rightOp) {
      CvRasterAPI.CvRaster_assign(leftOp.nativeObj, rightOp.nativeObj);
   }

   /**
    * You can use this method to create a {@link CvMat}
    * given a native pointer to the location of the raw data, and the metadata for the
    * {@code Mat}. Since the data is being passed to the underlying {@code Mat}, the {@code Mat}
    * will not be the "owner" of the data. That means YOU need to make sure that the native
    * data buffer outlives the CvRaster or you're pretty much guaranteed a core dump.
    */
   public static CvMat create(final int rows, final int cols, final int type, final long pointer) {
      final long nativeObj = CvRasterAPI.CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
      if(nativeObj == 0)
         throw new NullPointerException("Cannot create a CvMat from a null pointer data buffer.");
      return CvMat.wrapNative(nativeObj);
   }

   public CvMat returnMe() {
      // hacky, yet efficient.
      skipCloseOnceForReturn = true;
      return this;
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
}
