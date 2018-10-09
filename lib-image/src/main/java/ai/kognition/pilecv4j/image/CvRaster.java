package ai.kognition.pilecv4j.image;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.sun.jna.Pointer;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

/**
 * <p>
 * This class is an easier (perhaps) and more efficient interface to an OpenCV Mat.
 * Than the one available through the Java wrapper.
 * </p>
 * 
 * <p>
 * <b>NOTE: This class only works when {@code Mat.isContinuous()} is {@code true}</b>
 * </p>
 * 
 * <p>
 * To use it you as {@code CvRaster} to "manage" a {@code Mat}. Using the {@code Mat} through
 * the {@code CvRaster} will then operate directly on the image data in the {@code Mat} in a
 * "zero copy" form. When the {@code CvRaster} is closed, the underlying {@code Mat} will be
 * "released" if the {@code CvRaster} is managing the {@code Mat}.
 * </p>
 * 
 * <p>
 * To obtain a {@code CvRaster} you use the {@link CvRaster#move(Mat)} as follows.
 * </p>
 * 
 * <pre>
 * <code>
 * try (CvRaster raster = CvRaster.manage(mat);) {
 *      // do something with the raster
 * } // raster closed and Mat released
 * </code>
 * </pre>
 * 
 * <p>
 * If the {@code Mat} will outlive the {@code CvRaster} then you can use the {@link CvRaster#unmanaged(Mat)}
 * method to obtain the CvRaster. In this case you need to ensure that the {@code Mat} is not released
 * prior to the last use of the {@code CvRaster} which should be to {@code close} it.
 * </p>
 */
public abstract class CvRaster implements AutoCloseable {

   static {
      CvRasterAPI._init();
   }

   private boolean decoupled = false;
   private final CvMat mat;
   protected ByteBuffer currentBuffer = null;
   private int refCountForCurrentBuffer = 0;

   private CvRaster(final CvMat m) {
      this.mat = m;
   }

   /**
    * If you're going to use OpenCv classes before using CvRaster then
    * you need to bootstrap the initializing of OpenCv (which boils down
    * to loading the native libraries). This can be done by calling
    * this method.
    */
   // causes the classloader to initialize API and load the native libs
   public static void initOpenCv() {}

   /**
    * return the {@code CvType} of the {@code CvRaster}'s underlying {@code Mat}.
    */
   public int type() {
      return mat.type();
   }

   /**
    * return the number of channels of the {@code CvRaster}'s underlying {@code Mat}.
    */
   public int channels() {
      return CvType.channels(type());
   }

   /**
    * return the number of rows of the {@code CvRaster}'s underlying {@code Mat}.
    */
   public int rows() {
      return mat.rows();
   }

   /**
    * return the number of columns of the {@code CvRaster}'s underlying {@code Mat}.
    */
   public int cols() {
      return mat.cols();
   }

   /**
    * return the element size of the {@code CvRaster}'s underlying {@code Mat}. This uses
    * {@code CvType.ELEM_SIZE(type())}
    */
   public int elemSize() {
      return CvType.ELEM_SIZE(type());
   }

   @FunctionalInterface
   public static interface ImageOpContext extends QuietCloseable {}

   /**
    * This is NOT thread safe.
    * TODO: Make this thread safe without locking
    */
   public ImageOpContext imageOp() {
      refCountForCurrentBuffer++;
      if(currentBuffer == null) {
         currentBuffer = getData(mat);
         prep();
      }

      return () -> {
         refCountForCurrentBuffer--;
         if(refCountForCurrentBuffer <= 0)
            currentBuffer = null;
      };
   }

   /**
    * Direct access to the underlying {@code Mat}'s data buffer is available, as long
    * as the underlying buffer is continuous.
    */
   public ByteBuffer underlying() {
      if(currentBuffer == null)
         throw new NullPointerException("You cannot perform this operation without opening an \"imageOp\" on the " + CvRaster.class.getSimpleName());
      return currentBuffer;
   }

   /**
    * This call should be made to hand management of the Mat over to the {@link CvRaster}.
    * The Mat will be {@link CvMat#move(Mat)}ed so the Mat shouldn't be used after this
    * call or, at least, it shouldn't be assumed to still be pointing to the same image
    * data. When the CvRaster is closed, it will release the data that was originally
    * associated with the {@code Mat}. If you want to keep the {@code Mat} beyond the
    * life of the {@link CvRaster} then consider using {@link CvRaster#manageShallowCopy(Mat)}.
    */
   public static CvRaster move(final Mat mat) {
      return move(mat, null);
   }

   /**
    * This call should be made to manage a copy of the Mat using a {@link CvRaster}.
    * NOTE!! Changes to the {@link CvRaster} will be reflected in the {@link Mat} and
    * vs. vrs. If you want a deep copy/clone of the original Mat then consider
    * using {@link CvRaster#manageDeepCopy(Mat)}.
    */
   public static CvRaster manageShallowCopy(final Mat mat) {
      return manageShallowCopy(mat, null);
   }

   /**
    * This call will manage a complete deep copy of the provided {@code Mat}.
    * Changes in one will not be reflected in the other.
    */
   public static CvRaster manageDeepCopy(final Mat mat) {
      return manageDeepCopy(mat, null);
   }

   /**
    * <p>
    * This call should be made to hand management of the Mat over to the {@link CvRaster}.
    * The Mat will be {@link CvMat#move(Mat)}ed so the Mat shouldn't be used after this
    * call or, at least, it shouldn't be assumed to still be pointing to the same image
    * data. When the CvRaster is closed, it will release the data that was originally
    * associated with the {@code Mat}. If you want to keep the {@code Mat} beyond the
    * life of the {@link CvRaster} then consider using {@link CvRaster#manageShallowCopy(Mat)}.
    * </p>
    * 
    * <p>
    * The {@link ai.kognition.pilecv4j.image.CvRaster.Closer} is an {@link AutoCloseable} context
    * that this {@link CvRaster} will be added to so that then it closes, this {@link CvRaster}
    * will also be closed {@code release}-ing the underlying {@code Mat}.
    */
   public static CvRaster move(final Mat mat, final Closer closer) {
      return toRaster(CvMat.move(mat), closer);
   }

   /**
    * <p>
    * This call should be made to manage a copy of the Mat using a {@link CvRaster}.
    * NOTE!! Changes to the {@link CvRaster} will be reflected in the {@link Mat} and
    * vs. vrs. If you want a deep copy/clone of the original Mat then consider
    * using {@link CvRaster#manageDeepCopy(Mat)}.
    * </p>
    * 
    * <p>
    * The {@link ai.kognition.pilecv4j.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster}
    * will be added to so that then it closes, this {@link CvRaster} will also be closed
    * {@code release}-ing the underlying {@code Mat}.
    */
   public static CvRaster manageShallowCopy(final Mat mat, final Closer closer) {
      final CvRaster ret = makeInstance(CvMat.shallowCopy(mat));
      if(closer != null)
         closer.add(ret);
      return ret;
   }

   /**
    * This call will manage a complete deep copy of the provided {@code Mat}.
    * Changes in one will not be reflected in the other.
    * 
    * <p>
    * The {@link ai.kognition.pilecv4j.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster}
    * will be added to so that then it closes, this {@link CvRaster} will also be closed
    * {@code release}-ing the underlying {@code Mat}.
    */
   public static CvRaster manageDeepCopy(final Mat mat, final Closer closer) {
      if(mat.rows() == 0)
         return move(new Mat(mat.rows(), mat.cols(), mat.type()));
      if(mat.isContinuous())
         return move(mat.clone(), closer);

      final Mat newMat = new Mat(mat.rows(), mat.cols(), mat.type());
      mat.copyTo(newMat);
      return move(newMat, closer);
   }

   /**
    * You can use this method to create a {@link CvRaster} along with it's managed Mat.
    * It's equivalent to {@code CvRaster.manage(Mat.zeros(rows, cols, type)); }
    * 
    * @see CvRaster#move(Mat)
    */
   public static CvRaster create(final int rows, final int cols, final int type) {
      return create(rows, cols, type, null);
   }

   /**
    * You can use this method to create a {@link CvRaster} along with it's managed Mat.
    * It's equivalent to {@code CvRaster.manage(Mat.zeros(rows, cols, type)); }
    * 
    * <p>
    * The {@link ai.kognition.pilecv4j.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster}
    * will be added to so that then it closes, this {@link CvRaster} will also be closed.
    * 
    * @see CvRaster#move(Mat, ai.kognition.pilecv4j.image.CvRaster.Closer)
    */
   public static CvRaster create(final int rows, final int cols, final int type, final Closer closer) {
      return toRaster(CvMat.zeros(rows, cols, type), closer);
   }

   /**
    * You can use this method to create a {@link CvRaster} along with it's managed Mat
    * given a native pointer to the location of the raw data and the metadata for the
    * {@code Mat}. Since the data is being passed to the underlying {@code Mat}, the {@code Mat}
    * will not be the "owner" of the data. That means YOU need to make sure that the native
    * data buffer outlives the CvRaster or you're pretty much guaranteed a code dump.
    */
   public static CvRaster create(final int rows, final int cols, final int type, final long pointer) {
      return create(rows, cols, type, pointer, null);
   }

   /**
    * You can use this method to create a {@link CvRaster} along with it's managed Mat
    * given a native pointer to the location of the raw data and the metadata for the
    * {@code Mat}. Since the data is being passed to the underlying {@code Mat}, the {@code Mat}
    * will not be the "owner" of the data. That means YOU need to make sure that the native
    * data buffer outlives the CvRaster or you're pretty much guaranteed a code dump.
    * 
    * <p>
    * The {@link ai.kognition.pilecv4j.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster}
    * will be added to so that then it closes, this {@link CvRaster} will also be closed.
    */
   public static CvRaster create(final int rows, final int cols, final int type, final long pointer, final Closer closer) {
      final long nativeObj = CvRasterAPI.CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
      if(nativeObj == 0)
         throw new IllegalArgumentException("Cannot create a CvRaster from a Mat without a continuous buffer.");
      return toRaster(CvMat.wrapNative(nativeObj), closer);
   }

   private static CvRaster makeInstance(final CvMat mat) {
      // bb.clear();
      final int type = mat.type();
      final int depth = CvType.depth(type);

      switch(depth) {
         case CvType.CV_8S:
         case CvType.CV_8U:
            return new CvRaster(mat/* , bb */) {
               final byte[] zeroPixel = new byte[channels()];
               ByteBuffer bb = null;

               @Override
               protected void prep() {
                  bb = currentBuffer;
               }

               @Override
               public void zero(final int row, final int col) {
                  set(row, col, zeroPixel);
               }

               @Override
               public Object get(final int pos) {
                  final byte[] ret = new byte[channels()];
                  bb.position(pos);
                  bb.get(ret);
                  return ret;
               }

               @Override
               public void set(final int pos, final Object pixel) {
                  final byte[] p = (byte[])pixel;
                  bb.position(pos);
                  bb.put(p);
               }

               @Override
               public <T> void forEach(final PixelConsumer<T> pc) {
                  final BytePixelConsumer bpc = (BytePixelConsumer)pc;
                  final byte[] pixel = new byte[channels()];
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     bb.position(rowOffset + (col * channels));
                     bb.get(pixel);
                     bpc.accept(row, col, pixel);
                  });
               }

               @Override
               public <T> void apply(final PixelSetter<T> ps) {
                  final BytePixelSetter bps = (BytePixelSetter)ps;
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     bb.position(rowOffset + (col * channels));
                     bb.put(bps.pixel(row, col));
                  });
               }

               @Override
               public <T> void apply(final FlatPixelSetter<T> ps) {
                  final FlatBytePixelSetter bps = (FlatBytePixelSetter)ps;
                  iterateOver(pos -> {
                     bb.position(pos);
                     bb.put(bps.pixel(pos));
                  });
               }

            };
         case CvType.CV_16U:
         case CvType.CV_16S:
            return new CvRaster(mat/* , bb */) {
               ShortBuffer sb = null;
               final short[] zeroPixel = new short[channels()]; // zeroed already

               @Override
               protected void prep() {

                  sb = currentBuffer.asShortBuffer();
               }

               @Override
               public void zero(final int row, final int col) {
                  set(row, col, zeroPixel);
               }

               @Override
               public Object get(final int pos) {
                  final short[] ret = new short[channels()];
                  sb.position(pos);
                  sb.get(ret);
                  return ret;
               }

               @Override
               public void set(final int pos, final Object pixel) {
                  final short[] p = (short[])pixel;
                  sb.position(pos);
                  sb.put(p);
               }

               @Override
               public <T> void forEach(final PixelConsumer<T> pc) {
                  final ShortPixelConsumer bpc = (ShortPixelConsumer)pc;
                  final short[] pixel = new short[channels()];
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     sb.position(rowOffset + (col * channels));
                     sb.get(pixel);
                     bpc.accept(row, col, pixel);
                  });
               }

               @Override
               public <T> void apply(final PixelSetter<T> ps) {
                  final ShortPixelSetter bps = (ShortPixelSetter)ps;
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     sb.position(rowOffset + (col * channels));
                     sb.put(bps.pixel(row, col));
                  });
               }

               @Override
               public <T> void apply(final FlatPixelSetter<T> ps) {
                  final FlatShortPixelSetter bps = (FlatShortPixelSetter)ps;
                  iterateOver(pos -> {
                     sb.position(pos);
                     sb.put(bps.pixel(pos));
                  });
               }
            };
         case CvType.CV_32S:
            return new CvRaster(mat/* , bb */) {
               IntBuffer ib = null;
               final int[] zeroPixel = new int[channels()]; // zeroed already

               @Override
               protected void prep() {
                  ib = currentBuffer.asIntBuffer();
               }

               @Override
               public void zero(final int row, final int col) {
                  set(row, col, zeroPixel);
               }

               @Override
               public Object get(final int pos) {
                  final int[] ret = new int[channels()];
                  ib.position(pos);
                  ib.get(ret);
                  return ret;
               }

               @Override
               public void set(final int pos, final Object pixel) {
                  final int[] p = (int[])pixel;
                  ib.position(pos);
                  ib.put(p);
               }

               @Override
               public <T> void forEach(final PixelConsumer<T> pc) {
                  final IntPixelConsumer bpc = (IntPixelConsumer)pc;
                  final int[] pixel = new int[channels()];
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     ib.position(rowOffset + (col * channels));
                     ib.get(pixel);
                     bpc.accept(row, col, pixel);
                  });
               }

               @Override
               public <T> void apply(final PixelSetter<T> ps) {
                  final IntPixelSetter bps = (IntPixelSetter)ps;
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     ib.position(rowOffset + (col * channels));
                     ib.put(bps.pixel(row, col));
                  });
               }

               @Override
               public <T> void apply(final FlatPixelSetter<T> ps) {
                  final FlatIntPixelSetter bps = (FlatIntPixelSetter)ps;
                  iterateOver(pos -> {
                     ib.position(pos);
                     ib.put(bps.pixel(pos));
                  });
               }
            };
         case CvType.CV_32F:
            return new CvRaster(mat/* , bb */) {
               FloatBuffer fb = null;
               final float[] zeroPixel = new float[channels()]; // zeroed already

               @Override
               protected void prep() {
                  fb = currentBuffer.asFloatBuffer();
               }

               @Override
               public void zero(final int row, final int col) {
                  set(row, col, zeroPixel);
               }

               @Override
               public Object get(final int pos) {
                  final float[] ret = new float[channels()];
                  fb.position(pos);
                  fb.get(ret);
                  return ret;
               }

               @Override
               public void set(final int pos, final Object pixel) {
                  final float[] p = (float[])pixel;
                  fb.position(pos);
                  fb.put(p);
               }

               @Override
               public <T> void forEach(final PixelConsumer<T> pc) {
                  final FloatPixelConsumer bpc = (FloatPixelConsumer)pc;
                  final float[] pixel = new float[channels()];
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     fb.position(rowOffset + (col * channels));
                     fb.get(pixel);
                     bpc.accept(row, col, pixel);
                  });
               }

               @Override
               public <T> void apply(final PixelSetter<T> ps) {
                  final FloatPixelSetter bps = (FloatPixelSetter)ps;
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     fb.position(rowOffset + (col * channels));
                     fb.put(bps.pixel(row, col));
                  });
               }

               @Override
               public <T> void apply(final FlatPixelSetter<T> ps) {
                  final FlatFloatPixelSetter bps = (FlatFloatPixelSetter)ps;
                  iterateOver(pos -> {
                     fb.position(pos);
                     fb.put(bps.pixel(pos));
                  });
               }
            };
         case CvType.CV_64F:
            return new CvRaster(mat/* , bb */) {
               DoubleBuffer db = null;
               final double[] zeroPixel = new double[channels()]; // zeroed already

               @Override
               protected void prep() {
                  db = currentBuffer.asDoubleBuffer();
               }

               @Override
               public void zero(final int row, final int col) {
                  set(row, col, zeroPixel);
               }

               @Override
               public Object get(final int pos) {
                  final double[] ret = new double[channels()];
                  db.position(pos);
                  db.get(ret);
                  return ret;
               }

               @Override
               public void set(final int pos, final Object pixel) {
                  final double[] p = (double[])pixel;
                  db.position(pos);
                  db.put(p);
               }

               @Override
               public <T> void forEach(final PixelConsumer<T> pc) {
                  final DoublePixelConsumer bpc = (DoublePixelConsumer)pc;
                  final double[] pixel = new double[channels()];
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     db.position(rowOffset + (col * channels));
                     db.get(pixel);
                     bpc.accept(row, col, pixel);
                  });
               }

               @Override
               public <T> void apply(final PixelSetter<T> ps) {
                  final DoublePixelSetter bps = (DoublePixelSetter)ps;
                  final int channels = channels();
                  iterateOver((row, col, rowOffset) -> {
                     db.position(rowOffset + (col * channels));
                     db.put(bps.pixel(row, col));
                  });
               }

               @Override
               public <T> void apply(final FlatPixelSetter<T> ps) {
                  final FlatDoublePixelSetter bps = (FlatDoublePixelSetter)ps;
                  iterateOver(pos -> {
                     db.position(pos);
                     db.put(bps.pixel(pos));
                  });
               }
            };
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   /**
    * zero out the pixel at the position (row, col)
    */
   public abstract void zero(int row, int col);

   /**
    * get the pixel at the flattened position {@code pos}. The type will
    * depend on the underlying CvType.
    */
   public abstract Object get(int pos); // flat get

   /**
    * Set the {@code pos}ition in the raster to the provided pixel value. The
    * pixel value will need to comport with the CvType or you'll get an exception.
    * For example, if the CvType is {@code CvType.CV_16SC3} then the pixel
    * needs to be {@code short[3]}.
    */
   public abstract void set(int pos, Object pixel);

   /**
    * Apply the given lambda to every pixel.
    */
   public abstract <T> void apply(final PixelSetter<T> pixelSetter);

   /**
    * Apply the given lambda to every pixel.
    */
   public abstract <T> void apply(final FlatPixelSetter<T> pixelSetter);

   /**
    * Apply the given lambda to every pixel.
    */
   public abstract <T> void forEach(final PixelConsumer<T> consumer);

   protected abstract void prep();

   /**
    * Get the pixel for the given row/col location. The pixel value will comport with the
    * CvType of the raster. For example, if the CvType is {@code CvType.CV_16SC3} then the pixel
    * will be {@code short[3]}.
    */
   public Object get(final int row, final int col) {
      final int channels = channels();
      return get((row * cols() * channels) + (col * channels));
   }

   /**
    * Set the row/col position in the raster to the provided pixel value. The
    * pixel value will need to comport with the CvType or you'll get an exception.
    * For example, if the CvType is {@code CvType.CV_16SC3} then the pixel
    * needs to be {@code short[3]}.
    */
   public void set(final int row, final int col, final Object pixel) {
      final int channels = channels();
      set((row * cols() * channels) + (col * channels), pixel);
   }

   /**
    * Reduce the raster to a single value of type {@code U} by applying the aggregator
    */
   public <U> U reduce(final U identity, final PixelAggregate<Object, U> seqOp) {
      U prev = identity;
      final int rows = rows();
      final int cols = cols();
      for(int r = 0; r < rows; r++) {
         for(int c = 0; c < cols; c++) {
            prev = seqOp.apply(prev, get(r, c), r, c);
         }
      }
      return prev;
   }

   /**
    * The total number of bytes in the raster.
    */
   public int getNumBytes() {
      return rows() * cols() * elemSize();
   }

   /**
    * The underlying data buffer pointer as a long
    */
   public long getNativeAddressOfData() {
      if(!mat.isContinuous())
         throw new IllegalArgumentException("Cannot create a CvRaster from a Mat without a continuous buffer.");
      return Pointer.nativeValue(CvRasterAPI.CvRaster_getData(mat.nativeObj));
   }

   /**
    * The underlying {@code Mat} is guarded because it cannot (currently) be modified such that
    * the data buffer changes location without breaking the CvRaster. This allows you access to the
    * {@code Mat} anyway. Just make sure you know what you're doing.
    */
   public <R> R matOp(final Function<CvMat, R> op) {
      try (final ImageOpContext io = imageOp()) {
         return op.apply(mat);
      }
   }

   /**
    * The underlying {@code Mat} is guarded because it cannot (currently) be modified such that
    * the data buffer changes location without breaking the CvRaster. This allows you access to the
    * {@code Mat} anyway. Just make sure you know what you're doing.
    */
   public void matAp(final Consumer<CvMat> op) {
      try (final ImageOpContext io = imageOp()) {
         op.accept(mat);
      }
   }

   /**
    * This method will free the underlying {@code Mat} from being owned by this CvRaster.
    * When a CvRaster doesn't own the underlying {@code Mat} it doesn't free it up when
    * it's {@code close}ed.
    */
   public CvMat disown() {
      decoupled = true;
      return mat;
   }

   /**
    * This will free any owned underlying resources. You should not attempt to use the
    * CvRaster once it's closed.
    */
   @Override
   public void close() {
      if(mat != null && !decoupled)
         mat.close();
      decoupled = true;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + channels();
      result = prime * result + cols();
      result = prime * result + elemSize();
      result = prime * result + rows();
      result = prime * result + type();
      return result;
   }

   @Override
   public boolean equals(final Object obj) {
      if(this == obj)
         return true;
      if(obj == null)
         return false;
      if(getClass() != obj.getClass())
         return false;
      final CvRaster other = (CvRaster)obj;
      if(channels() != other.channels())
         return false;
      if(cols() != other.cols())
         return false;
      if(elemSize() != other.elemSize())
         return false;
      if(mat == null) {
         if(other.mat != null)
            return false;
      } else if(other.mat == null)
         return false;
      if(rows() != other.rows())
         return false;
      if(type() != other.type())
         return false;
      if(mat != other.mat && !pixelsIdentical(mat, other.mat))
         return false;
      return true;
   }

   /**
    * This is a helper comparator that verifies the byte by byte equivalent of the two
    * underlying data buffers.
    */
   public static boolean pixelsIdentical(final Mat m1, final Mat m2) {
      if(m1.nativeObj == m2.nativeObj)
         return true;
      final ByteBuffer bb1 = _getData(m1);
      final ByteBuffer bb2 = _getData(m2);
      return bb1.compareTo(bb2) == 0;
   }

   public static <T> T copyToPrimitiveArray(final CvRaster m) {
      return copyToPrimitiveArray(m.mat);
   }

   @SuppressWarnings("unchecked")
   public static <T> T copyToPrimitiveArray(final Mat m) {
      final int rows = m.rows();
      final int cols = m.cols();
      final int type = m.type();
      final int channels = CvType.channels(type);
      final int depth = CvType.depth(type);

      switch(depth) {
         case CvType.CV_8S:
         case CvType.CV_8U: {
            final byte[] data = new byte[rows * cols * channels];

            m.get(0, 0, data);
            return (T)data;
         }
         case CvType.CV_16U:
         case CvType.CV_16S: {
            final short[] data = new short[rows * cols * channels];
            m.get(0, 0, data);
            return (T)data;
         }
         case CvType.CV_32S: {
            final int[] data = new int[rows * cols * channels];
            m.get(0, 0, data);
            return (T)data;
         }
         case CvType.CV_32F: {
            final float[] data = new float[rows * cols * channels];
            m.get(0, 0, data);
            return (T)data;
         }
         case CvType.CV_64F: {
            final double[] data = new double[rows * cols * channels];
            m.get(0, 0, data);
            return (T)data;
         }
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   @FunctionalInterface
   public static interface GetChannelValueAsInt {
      public int get(Object pixel, int channel);
   }

   @FunctionalInterface
   public static interface PutChannelValueFromInt {
      public void put(Object pixel, int channel, int channelValue);
   }

   @FunctionalInterface
   public static interface PixelToInts extends Function<Object, int[]> {}

   @FunctionalInterface
   public static interface IntsToPixel extends Function<int[], Object> {}

   public static PutChannelValueFromInt channelValuePutter(final CvRaster raster) {
      switch(CvType.depth(raster.type())) {
         case CvType.CV_8S:
            return (p, ch, chv) -> ((byte[])p)[ch] = (byte)((chv > Byte.MAX_VALUE) ? Byte.MAX_VALUE : chv);
         case CvType.CV_8U:
            return (p, ch, chv) -> ((byte[])p)[ch] = (byte)((chv > 0xFF) ? 0xFF : chv);
         case CvType.CV_16S:
            return (p, ch, chv) -> ((short[])p)[ch] = (short)((chv > Short.MAX_VALUE) ? Short.MAX_VALUE : chv);
         case CvType.CV_16U:
            return (p, ch, chv) -> ((short[])p)[ch] = (short)((chv > 0xFFFF) ? 0XFFFF : chv);
         case CvType.CV_32S:
            return (p, ch, chv) -> ((int[])p)[ch] = chv;
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type()));
      }
   }

   public static GetChannelValueAsInt channelValueFetcher(final CvRaster raster) {
      switch(CvType.depth(raster.type())) {
         case CvType.CV_8S:
            return (p, ch) -> (int)((byte[])p)[ch];
         case CvType.CV_8U:
            return (p, ch) -> (((byte[])p)[ch] & 0xFF);
         case CvType.CV_16S:
            return (p, ch) -> (int)((short[])p)[ch];
         case CvType.CV_16U:
            return (p, ch) -> (((short[])p)[ch] & 0xFFFF);
         case CvType.CV_32S:
            return (p, ch) -> ((int[])p)[ch];
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type()));
      }
   }

   public static PixelToInts pixelToIntsConverter(final CvRaster raster) {
      final GetChannelValueAsInt fetcher = channelValueFetcher(raster);
      final int numChannels = raster.channels();
      final int[] ret = new int[numChannels];
      return(p -> {
         for(int i = 0; i < numChannels; i++)
            ret[i] = fetcher.get(p, i);
         return ret;
      });
   }

   public static int numChannelElementValues(final CvRaster raster) {
      switch(CvType.depth(raster.type())) {
         case CvType.CV_8S:
         case CvType.CV_8U:
            return 256;
         case CvType.CV_16S:
         case CvType.CV_16U:
            return 65536;
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type()));
      }
   }

   public static Object makePixel(final CvRaster raster) {
      return makePixel(raster.type());
   }

   public static Object makePixel(final int type) {
      final int channels = CvType.channels(type);
      switch(CvType.depth(type)) {
         case CvType.CV_8S:
         case CvType.CV_8U:
            return new byte[channels];
         case CvType.CV_16S:
         case CvType.CV_16U:
            return new short[channels];
         case CvType.CV_32S:
            return new int[channels];
         case CvType.CV_32F:
            return new float[channels];
         case CvType.CV_64F:
            return new double[channels];
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   public static PixelConsumer<?> makePixelPrinter(final PrintStream stream, final int type) {
      switch(CvType.depth(type)) {
         case CvType.CV_8S:
         case CvType.CV_8U:
            return (BytePixelConsumer)(final int r, final int c, final byte[] pixel) -> stream.print(Arrays.toString(pixel));
         case CvType.CV_16S:
         case CvType.CV_16U:
            return (ShortPixelConsumer)(final int r, final int c, final short[] pixel) -> stream.print(Arrays.toString(pixel));
         case CvType.CV_32S:
            return (IntPixelConsumer)(final int r, final int c, final int[] pixel) -> stream.print(Arrays.toString(pixel));
         case CvType.CV_32F:
            return (FloatPixelConsumer)(final int r, final int c, final float[] pixel) -> stream.print(Arrays.toString(pixel));
         case CvType.CV_64F:
            return (DoublePixelConsumer)(final int r, final int c, final double[] pixel) -> stream.print(Arrays.toString(pixel));
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   public static IntsToPixel intsToPixelConverter(final CvRaster raster) {
      final PutChannelValueFromInt putter = channelValuePutter(raster);
      final int numChannels = raster.channels();
      final Object pixel = makePixel(raster);
      return ints -> {
         for(int i = 0; i < numChannels; i++)
            putter.put(pixel, i, ints[i]);
         return pixel;
      };
   }

   // ==================================================================
   // PixelConsumer interfaces
   // ==================================================================
   @FunctionalInterface
   public static interface PixelConsumer<T> {
      public void accept(int row, int col, T pixel);
   }

   @FunctionalInterface
   public static interface BytePixelConsumer extends PixelConsumer<byte[]> {}

   @FunctionalInterface
   public static interface ShortPixelConsumer extends PixelConsumer<short[]> {}

   @FunctionalInterface
   public static interface IntPixelConsumer extends PixelConsumer<int[]> {}

   @FunctionalInterface
   public static interface FloatPixelConsumer extends PixelConsumer<float[]> {}

   @FunctionalInterface
   public static interface DoublePixelConsumer extends PixelConsumer<double[]> {}

   // ==================================================================
   // PixelSetter interfaces
   // ==================================================================
   @FunctionalInterface
   public static interface PixelSetter<T> {
      public T pixel(int row, int col);
   }

   @FunctionalInterface
   public static interface BytePixelSetter extends PixelSetter<byte[]> {}

   @FunctionalInterface
   public static interface ShortPixelSetter extends PixelSetter<short[]> {}

   @FunctionalInterface
   public static interface IntPixelSetter extends PixelSetter<int[]> {}

   @FunctionalInterface
   public static interface FloatPixelSetter extends PixelSetter<float[]> {}

   @FunctionalInterface
   public static interface DoublePixelSetter extends PixelSetter<double[]> {}
   // ==================================================================

   // ==================================================================
   // FlatPixelSetter interfaces
   // ==================================================================
   @FunctionalInterface
   public static interface FlatPixelSetter<T> {
      public T pixel(int position);
   }

   @FunctionalInterface
   public static interface FlatBytePixelSetter extends FlatPixelSetter<byte[]> {}

   @FunctionalInterface
   public static interface FlatShortPixelSetter extends FlatPixelSetter<short[]> {}

   @FunctionalInterface
   public static interface FlatIntPixelSetter extends FlatPixelSetter<int[]> {}

   @FunctionalInterface
   public static interface FlatFloatPixelSetter extends FlatPixelSetter<float[]> {}

   @FunctionalInterface
   public static interface FlatDoublePixelSetter extends FlatPixelSetter<double[]> {}
   // ==================================================================

   public static class Closer implements AutoCloseable {
      private final List<AutoCloseable> rastersToClose = new LinkedList<>();
      private final List<Mat> rawMats = new LinkedList<>();

      public <T extends AutoCloseable> T add(final T mat) {
         if(mat != null)
            rastersToClose.add(0, mat);
         return mat;
      }

      public <T extends Mat> T addMat(final T mat) {
         if(mat != null)
            rawMats.add(0, mat);
         return mat;
      }

      @Override
      public void close() {
         rastersToClose.stream().forEach(r -> Functional.uncheck(() -> r.close()));
         rawMats.stream().forEach(r -> CvMat.move(r).close());
      }

      public void release() {
         rastersToClose.clear();
      }
   }

   @FunctionalInterface
   public static interface PixelAggregate<P, R> {
      R apply(R prev, P pixel, int row, int col);
   }

   protected static interface PixelIterator {
      public void accept(int row, int col, int rowOffset);
   }

   protected void iterateOver(final PixelIterator piter) {
      final int rows = rows();
      final int cols = cols();
      final int channels = channels();
      final int colsXchannels = cols * channels;
      try (ImageOpContext io = imageOp()) {
         for(int row = 0; row < rows; row++) {
            final int rowOffset = row * colsXchannels;
            for(int col = 0; col < cols; col++) {
               piter.accept(row, col, rowOffset);
            }
         }
      }
   }

   protected static interface FlatPixelIterator {
      public void accept(int pos);
   }

   protected void iterateOver(final FlatPixelIterator piter) {
      final int rows = rows();
      final int cols = cols();
      final int channels = channels();
      final int numElements = (rows * cols * channels);

      try (ImageOpContext io = imageOp()) {
         for(int pos = 0; pos < numElements; pos += channels) {
            piter.accept(pos);
         }
      }
   }

   private static ByteBuffer _getData(final Mat mat) {
      if(!mat.isContinuous())
         throw new IllegalArgumentException("Cannot create a CvRaster from a Mat without a continuous buffer.");
      final Pointer dataPtr = CvRasterAPI.CvRaster_getData(mat.nativeObj);
      if(Pointer.nativeValue(dataPtr) == 0)
         throw new IllegalArgumentException("Cannot access raw data in Mat. It may be uninitialized.");
      return dataPtr.getByteBuffer(0, mat.elemSize() * mat.total());
   }

   private static ByteBuffer getData(final Mat mat) {
      final ByteBuffer ret = _getData(mat);
      if(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
         ret.order(ByteOrder.LITTLE_ENDIAN);
      return ret;
   }

   /**
    * convert/wrap a CvMat into a CvRaster
    * 
    * <p>
    * The {@link ai.kognition.pilecv4j.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster}
    * will be added to so that then it closes, this {@link CvRaster} will also be closed
    * {@code release}-ing the underlying {@code Mat}.
    */
   private static CvRaster toRaster(final CvMat mat, final Closer closer) {
      final CvRaster ret = makeInstance(mat/* , getData(mat) */);
      if(closer != null)
         closer.add(ret);
      return ret;
   }
}
