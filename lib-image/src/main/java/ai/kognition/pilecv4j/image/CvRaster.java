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
import java.util.function.Function;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.sun.jna.Pointer;

import net.dempsy.util.Functional;

/**
 * <p>
 * {@link CvRaster} is a utility for direct access to the underlying Mat's data buffer
 * from java using a {@code DirectByteBuffer}. You can get access to an underlying
 * Mat's data buffer by passing a lambda to the appropriate CvMat method.
 * </p>
 * 
 * <pre>
 * <code>
 * CvMat.rasterAp(mat, raster -> {
 *   // do something with the raster which contains a DirectByteBuffer
 *   // that can be retrieved using:
 *   ByteBuffer bb = raster.underlying();
 * });
 * </code>
 * </pre>
 * 
 * Alternatively you can apply a lambda to the {@link CvRaster} using one of the available
 * methods. For example, this will add up all of the pixel values in grayscale byte image
 * and return the result
 * 
 * <pre>
 * <code>
 * GetChannelValueAsInt valueFetcher = CvRaster.channelValueFetcher(mat.type());
 * final long sum = CvMat.rasterOp(mat,
 *   raster -> raster.reduce(Long.valueOf(0), (prev, pixel, row, col) -> Long.valueOf(prev.longValue() + valueFetcher.get(pixel, 0))));
 * </code>
 * </pre>
 * 
 */
public abstract class CvRaster implements AutoCloseable {

   public final Mat mat;
   protected final ByteBuffer currentBuffer;

   private CvRaster(final Mat m) {
      this.mat = m;
      this.currentBuffer = getData(m);
   }

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

   /**
    * Apply the given lambda to every pixel.
    */
   public abstract <T> void forEach(final FlatPixelConsumer<T> consumer);

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

   @Override
   public void close() {
      // clean up the direct byte buffer?
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

   /**
    * Copy the entire image to a primitive array of the appropriate type.
    */
   public static <T> T copyToPrimitiveArray(final CvRaster m) {
      return copyToPrimitiveArray(m.mat);
   }

   /**
    * Copy the entire image to a primitive array of the appropriate type.
    */
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

   /**
    * Copy the entire image to a primitive array of the appropriate type.
    */
   public static <T> void copyToPrimitiveArray(final Mat m, final T data) {
      final int type = m.type();
      final int depth = CvType.depth(type);

      switch(depth) {
         case CvType.CV_8S:
         case CvType.CV_8U: {
            m.get(0, 0, (byte[])data);
            return;
         }
         case CvType.CV_16U:
         case CvType.CV_16S: {
            m.get(0, 0, (short[])data);
            return;
         }
         case CvType.CV_32S: {
            m.get(0, 0, (int[])data);
            return;
         }
         case CvType.CV_32F: {
            m.get(0, 0, (float[])data);
            return;
         }
         case CvType.CV_64F: {
            m.get(0, 0, (double[])data);
            return;
         }
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   /**
    * Instances of this interface will return the channel value of the given pixel as an int.
    * You can obtain an instance of this interface for the appropriate {@link CvType} using
    * {@link CvRaster#channelValueFetcher(int)}.
    */
   @FunctionalInterface
   public static interface GetChannelValueAsInt {
      public int get(Object pixel, int channel);
   }

   /**
    * Instances of this interface will set the channel value of the given pixel.
    * You can obtain an instance of this interface for the appropriate {@link CvType} using
    * {@link CvRaster#channelValuePutter(int)}.
    */
   @FunctionalInterface
   public static interface PutChannelValueFromInt {
      public void put(Object pixel, int channel, int channelValue);
   }

   /**
    * Create the appropriate {@link PutChannelValueAsInt} instance for the given type.
    */
   public static PutChannelValueFromInt channelValuePutter(final int type) {
      switch(CvType.depth(type)) {
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
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   /**
    * Create the appropriate {@link GetChannelValueAsInt} instance for the given type.
    */
   public static GetChannelValueAsInt channelValueFetcher(final int type) {
      switch(CvType.depth(type)) {
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
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
   }

   public static Function<Object, int[]> pixelToIntsConverter(final int type) {
      final GetChannelValueAsInt fetcher = channelValueFetcher(type);
      final int numChannels = CvType.channels(type);
      final int[] ret = new int[numChannels];
      return(p -> {
         for(int i = 0; i < numChannels; i++)
            ret[i] = fetcher.get(p, i);
         return ret;
      });
   }

   public static int numChannelElementValues(final int type) {
      switch(CvType.depth(type)) {
         case CvType.CV_8S:
         case CvType.CV_8U:
            return 256;
         case CvType.CV_16S:
         case CvType.CV_16U:
            return 65536;
         default:
            throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
      }
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

   public static Function<int[], Object> intsToPixelConverter(final int type) {
      final PutChannelValueFromInt putter = channelValuePutter(type);
      final int numChannels = CvType.channels(type);
      final Object pixel = makePixel(type);
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
   // FlatPixelConsumer interfaces
   // ==================================================================
   @FunctionalInterface
   public static interface FlatPixelConsumer<T> {
      public void accept(int pos, T pixel);
   }

   @FunctionalInterface
   public static interface FlatBytePixelConsumer extends FlatPixelConsumer<byte[]> {}

   @FunctionalInterface
   public static interface FlatShortPixelConsumer extends FlatPixelConsumer<short[]> {}

   @FunctionalInterface
   public static interface FlatIntPixelConsumer extends FlatPixelConsumer<int[]> {}

   @FunctionalInterface
   public static interface FlatFloatPixelConsumer extends FlatPixelConsumer<float[]> {}

   @FunctionalInterface
   public static interface FlatDoublePixelConsumer extends FlatPixelConsumer<double[]> {}

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

   static CvRaster makeInstance(final Mat mat) {
      final int type = mat.type();
      final int depth = CvType.depth(type);

      switch(depth) {
         case CvType.CV_8S:
         case CvType.CV_8U:
            return new CvRaster(mat) {
               final byte[] zeroPixel = new byte[channels()];
               ByteBuffer bb = currentBuffer;

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
               public <T> void forEach(final FlatPixelConsumer<T> pc) {
                  final FlatBytePixelConsumer bpc = (FlatBytePixelConsumer)pc;
                  final byte[] pixel = new byte[channels()];
                  final int channels = channels();
                  iterateOver((pos) -> {
                     bb.position(pos * channels);
                     bb.get(pixel);
                     bpc.accept(pos, pixel);
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
            return new CvRaster(mat) {
               ShortBuffer sb = currentBuffer.asShortBuffer();
               final short[] zeroPixel = new short[channels()]; // zeroed already

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
               public <T> void forEach(final FlatPixelConsumer<T> pc) {
                  final FlatShortPixelConsumer bpc = (FlatShortPixelConsumer)pc;
                  final short[] pixel = new short[channels()];
                  final int channels = channels();
                  iterateOver((pos) -> {
                     sb.position(pos * channels);
                     sb.get(pixel);
                     bpc.accept(pos, pixel);
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
            return new CvRaster(mat) {
               IntBuffer ib = currentBuffer.asIntBuffer();
               final int[] zeroPixel = new int[channels()]; // zeroed already

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
               public <T> void forEach(final FlatPixelConsumer<T> pc) {
                  final FlatIntPixelConsumer bpc = (FlatIntPixelConsumer)pc;
                  final int[] pixel = new int[channels()];
                  final int channels = channels();
                  iterateOver((pos) -> {
                     ib.position(pos * channels);
                     ib.get(pixel);
                     bpc.accept(pos, pixel);
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
            return new CvRaster(mat) {
               FloatBuffer fb = currentBuffer.asFloatBuffer();
               final float[] zeroPixel = new float[channels()]; // zeroed already

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
               public <T> void forEach(final FlatPixelConsumer<T> pc) {
                  final FlatFloatPixelConsumer bpc = (FlatFloatPixelConsumer)pc;
                  final float[] pixel = new float[channels()];
                  final int channels = channels();
                  iterateOver((pos) -> {
                     fb.position(pos * channels);
                     fb.get(pixel);
                     bpc.accept(pos, pixel);
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
            return new CvRaster(mat) {
               DoubleBuffer db = currentBuffer.asDoubleBuffer();
               final double[] zeroPixel = new double[channels()]; // zeroed already

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
               public <T> void forEach(final FlatPixelConsumer<T> pc) {
                  final FlatDoublePixelConsumer bpc = (FlatDoublePixelConsumer)pc;
                  final double[] pixel = new double[channels()];
                  final int channels = channels();
                  iterateOver((pos) -> {
                     db.position(pos * channels);
                     db.get(pixel);
                     bpc.accept(pos, pixel);
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

   protected static interface PixelIterator {
      public void accept(int row, int col, int rowOffset);
   }

   protected void iterateOver(final PixelIterator piter) {
      final int rows = rows();
      final int cols = cols();
      final int channels = channels();
      final int colsXchannels = cols * channels;
      for(int row = 0; row < rows; row++) {
         final int rowOffset = row * colsXchannels;
         for(int col = 0; col < cols; col++) {
            piter.accept(row, col, rowOffset);
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

      for(int pos = 0; pos < numElements; pos += channels) {
         piter.accept(pos);
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

}
