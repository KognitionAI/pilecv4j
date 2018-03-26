package com.jiminger.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.sun.jna.Pointer;

/**
 * <p>
 * This class is an easier (perhaps) and more efficient interface to an OpenCV Mat.
 * Than the one available through the Java wrapper.</p>
 * 
 * <p><b>NOTE: This class only works when {@code Mat.isContinuous()} is {@code true}</b></p>
 * 
 * <p>To use it you as {@code CvRaster} to "manage" a {@code Mat}. Using the {@code Mat} through
 * the {@code CvRaster} will then operate directly on the image data in the {@code Mat} in a
 * "zero copy" form. When the {@code CvRaster} is closed, the underlying {@code Mat} will be
 * "released" if the {@code CvRaster} is managing the {@code Mat}.
 * </p>
 * 
 * <p>
 * To obtain a {@code CvRaster} you use the {@link CvRaster#manage(Mat)} as follows.
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
 * <p>If the {@code Mat} will outlive the {@code CvRaster} then you can use the {@link CvRaster#unmanaged(Mat)} 
 * method to obtain the CvRaster. In this case you need to ensure that the {@code Mat} is not released
 * prior to the last use of the {@code CvRaster} which should be to {@code close} it.</p>
 */
public abstract class CvRaster implements AutoCloseable {

    private static final CvRasterAPI API = CvRasterAPI.API;

    /**
     * If you're going to use OpenCv classes before using CvRaster then
     * you need to bootstrap the initializing of OpenCv (which boils down
     * to loading the native libraries). This can be done by calling
     * this method.
     */
    // causes the classloader to initialize API and load the native libs
    public static void initOpenCv() {}

    public int type() {
        return mat.type();
    }

    public int channels() {
        return CvType.channels(type());
    }

    public int rows() {
        return mat.rows();
    }

    public int cols() {
        return mat.cols();
    }

    public int elemSize() {
        return CvType.ELEM_SIZE(type());
    }

    public final ByteBuffer underlying;
    private boolean decoupled = false;
    private final CvMat mat;

    private CvRaster(final CvMat m, final ByteBuffer underlying) {
        this.mat = m;
        this.underlying = underlying;
    }

    /**
     * This call should be made to hand management of the Mat over to the {@link CvRaster}. 
     * When the CvRaster is closed, it will release the {@code Mat}. If you want to keep 
     * the {@code Mat} beyond the life of the {@link CvRaster} then consider using 
     * {@link CvRaster#unmanaged(Mat)}.
     */
    public static CvRaster manageCopy(final Mat mat) {
        return manageCopy(mat, null);
    }

    /**
     * convert/wrap a CvMat into a CvRaster
     */
    public static CvRaster toRaster(final CvMat mat) {
        return toRaster(mat, null);
    }

    /**
     * <p>This call should be made to hand management of the Mat over to the {@link CvRaster}. 
     * When the CvRaster is closed, it will release the {@code Mat}. If you want to keep 
     * the {@code Mat} beyond the life of the {@link CvRaster} then consider using 
     * {@link CvRaster#unmanaged(Mat)}.</p>
     * 
     * <p>The {@link com.jiminger.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster} 
     * will be added to so that then it closes, this {@link CvRaster} will also be closed
     * {@code release}-ing the underlying {@code Mat}.
     */
    public static CvRaster manageCopy(final Mat mat, final Closer closer) {
        final CvRaster ret = makeInstance(new CvMat(API.CvRaster_copy(mat.nativeObj)), getData(mat));
        if (closer != null)
            closer.add(ret);
        return ret;
    }

    /**
     * convert/wrap a CvMat into a CvRaster
     * 
     * <p>The {@link com.jiminger.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster} 
     * will be added to so that then it closes, this {@link CvRaster} will also be closed
     * {@code release}-ing the underlying {@code Mat}.
     */
    public static CvRaster toRaster(final CvMat mat, final Closer closer) {
        final CvRaster ret = makeInstance(mat, getData(mat));
        if (closer != null)
            closer.add(ret);
        return ret;
    }

    /**
     * You can use this method to create a {@link CvRaster} along with it's managed Mat.
     * It's equivalent to {@code CvRaster.manage(Mat.zeros(rows, cols, type)); }
     * 
     * @see CvRaster#manage(Mat)
     */
    public static CvRaster createManaged(final int rows, final int cols, final int type) {
        return createManaged(rows, cols, type, null);
    }

    /**
     * You can use this method to create a {@link CvRaster} along with it's managed Mat.
     * It's equivalent to {@code CvRaster.manage(Mat.zeros(rows, cols, type)); }
     * 
     * <p>The {@link com.jiminger.image.CvRaster.Closer} is an {@link AutoCloseable} context that this {@link CvRaster} 
     * will be added to so that then it closes, this {@link CvRaster} will also be closed.
     * 
     * @see CvRaster#manage(Mat, com.jiminger.image.CvRaster.Closer)
     */
    public static CvRaster createManaged(final int rows, final int cols, final int type, final Closer closer) {
        return toRaster(CvMat.zeros(rows, cols, type), closer);
    }

    public static CvRaster createManaged(final int rows, final int cols, final int type, final long pointer) {
        return createManaged(rows, cols, type, pointer, null);
    }

    public static CvRaster createManaged(final int rows, final int cols, final int type, final long pointer, final Closer closer) {
        final long nativeObj = API.CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
        if (nativeObj == 0)
            throw new IllegalArgumentException("Cannot create a CvRaster from a Mat without a continuous buffer.");
        return toRaster(new CvMat(nativeObj), closer);
    }

    private static CvRaster makeInstance(final CvMat mat, final ByteBuffer bb) {
        bb.clear();
        final int type = mat.type();
        final int depth = CvType.depth(type);

        switch (depth) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return new CvRaster(mat, bb) {
                    final byte[] zeroPixel = new byte[channels()];

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
                        final byte[] p = (byte[]) pixel;
                        bb.position(pos);
                        bb.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final BytePixelSetter bps = (BytePixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int colsXchannels = cols * channels;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                bb.position(rowOffset + (col * channels));
                                bb.put(bps.pixel(row, col));
                            }
                        }
                    }

                    @Override
                    public <T> void apply(final FlatPixelSetter<T> ps) {
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();

                        final FlatBytePixelSetter bps = (FlatBytePixelSetter) ps;
                        final int numElements = (rows * cols * channels);

                        for (int pos = 0; pos < numElements; pos += channels) {
                            bb.position(pos);
                            bb.put(bps.pixel(pos));
                        }
                    }

                };
            case CvType.CV_16U:
            case CvType.CV_16S:
                return new CvRaster(mat, bb) {
                    final ShortBuffer sb = bb.asShortBuffer();

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
                        final short[] p = (short[]) pixel;
                        sb.position(pos);
                        sb.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final ShortPixelSetter bps = (ShortPixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int colsXchannels = cols * channels;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                sb.position(rowOffset + (col * channels));
                                sb.put(bps.pixel(row, col));
                            }
                        }
                    }

                    @Override
                    public <T> void apply(final FlatPixelSetter<T> ps) {
                        final FlatShortPixelSetter bps = (FlatShortPixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int numElements = (rows * cols * channels);

                        for (int pos = 0; pos < numElements; pos += channels) {
                            sb.position(pos);
                            sb.put(bps.pixel(pos));
                        }
                    }
                };
            case CvType.CV_32S:
                return new CvRaster(mat, bb) {
                    final IntBuffer ib = bb.asIntBuffer();
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
                        final int[] p = (int[]) pixel;
                        ib.position(pos);
                        ib.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final IntPixelSetter bps = (IntPixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int colsXchannels = cols * channels;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                ib.position(rowOffset + (col * channels));
                                ib.put(bps.pixel(row, col));
                            }
                        }
                    }

                    @Override
                    public <T> void apply(final FlatPixelSetter<T> ps) {
                        final FlatIntPixelSetter bps = (FlatIntPixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int numElements = (rows * cols * channels);

                        for (int pos = 0; pos < numElements; pos += channels) {
                            ib.position(pos);
                            ib.put(bps.pixel(pos));
                        }
                    }
                };
            case CvType.CV_32F:
                return new CvRaster(mat, bb) {
                    final FloatBuffer fb = bb.asFloatBuffer();
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
                        final float[] p = (float[]) pixel;
                        fb.position(pos);
                        fb.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final FloatPixelSetter bps = (FloatPixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int colsXchannels = cols * channels;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                fb.position(rowOffset + (col * channels));
                                fb.put(bps.pixel(row, col));
                            }
                        }
                    }

                    @Override
                    public <T> void apply(final FlatPixelSetter<T> ps) {
                        final FlatFloatPixelSetter bps = (FlatFloatPixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int numElements = (rows * cols * channels);

                        for (int pos = 0; pos < numElements; pos += channels) {
                            fb.position(pos);
                            fb.put(bps.pixel(pos));
                        }
                    }
                };
            case CvType.CV_64F:
                return new CvRaster(mat, bb) {
                    final DoubleBuffer db = bb.asDoubleBuffer();
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
                        final double[] p = (double[]) pixel;
                        db.position(pos);
                        db.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final DoublePixelSetter bps = (DoublePixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int colsXchannels = cols * channels;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                db.position(rowOffset + (col * channels));
                                db.put(bps.pixel(row, col));
                            }
                        }
                    }

                    @Override
                    public <T> void apply(final FlatPixelSetter<T> ps) {
                        final FlatDoublePixelSetter bps = (FlatDoublePixelSetter) ps;
                        final int rows = rows();
                        final int cols = cols();
                        final int channels = channels();
                        final int numElements = (rows * cols * channels);

                        for (int pos = 0; pos < numElements; pos += channels) {
                            db.position(pos);
                            db.put(bps.pixel(pos));
                        }
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

    public abstract void set(int pos, Object pixel);

    public abstract <T> void apply(final PixelSetter<T> pixelSetter);

    public abstract <T> void apply(final FlatPixelSetter<T> pixelSetter);

    public Object get(final int row, final int col) {
        final int channels = channels();
        return get((row * cols() * channels) + (col * channels));
    }

    public void set(final int row, final int col, final Object pixel) {
        final int channels = channels();
        set((row * cols() * channels) + (col * channels), pixel);
    }

    public <U> U reduce(final U identity, final PixelAggregate<Object, U> seqOp) {
        U prev = identity;
        final int rows = rows();
        final int cols = cols();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                prev = seqOp.apply(prev, get(r, c), r, c);
            }
        }
        return prev;
    }

    public int getNumBytes() {
        return rows() * cols() * elemSize();
    }

    public long getNativeAddressOfData() {
        if (!mat.isContinuous())
            throw new IllegalArgumentException("Cannot create a CvRaster from a Mat without a continuous buffer.");
        return Pointer.nativeValue(API.CvRaster_getData(mat.nativeObj));
    }

    public ByteBuffer dataAsByteBuffer() {
        return underlying;
    }

    public <R> R matOp(final Function<CvMat, R> op) {
        return op.apply(mat);
    }

    public void matAp(final Consumer<CvMat> op) {
        op.accept(mat);
    }

    public CvMat decoupled() {
        decoupled = true;
        return mat;
    }

    // public void show() {
    // CvRasterNative.showImage(mat.nativeObj);
    // }

    @Override
    public void close() {
        if (mat != null && !decoupled)
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final CvRaster other = (CvRaster) obj;
        if (channels() != other.channels())
            return false;
        if (cols() != other.cols())
            return false;
        if (elemSize() != other.elemSize())
            return false;
        if (mat == null) {
            if (other.mat != null)
                return false;
        } else if (other.mat == null)
            return false;
        if (rows() != other.rows())
            return false;
        if (type() != other.type())
            return false;
        if (mat != other.mat && !pixelsIdentical(mat, other.mat))
            return false;
        return true;
    }

    public static boolean pixelsIdentical(final Mat m1, final Mat m2) {
        if (m1.nativeObj == m2.nativeObj)
            return true;
        final ByteBuffer bb1 = _getData(m1);
        final ByteBuffer bb2 = _getData(m2);
        return bb1.compareTo(bb2) == 0;
    }

    private static ByteBuffer _getData(final Mat mat) {
        if (!mat.isContinuous())
            throw new IllegalArgumentException("Cannot create a CvRaster from a Mat without a continuous buffer.");
        final Pointer dataPtr = API.CvRaster_getData(mat.nativeObj);
        if (Pointer.nativeValue(dataPtr) == 0)
            throw new IllegalArgumentException("Cannot access raw data in Mat. It may be uninitialized.");
        return dataPtr.getByteBuffer(0, mat.elemSize() * mat.total());
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

        switch (depth) {
            case CvType.CV_8S:
            case CvType.CV_8U: {
                final byte[] data = new byte[rows * cols * channels];

                m.get(0, 0, data);
                return (T) data;
            }
            case CvType.CV_16U:
            case CvType.CV_16S: {
                final short[] data = new short[rows * cols * channels];
                m.get(0, 0, data);
                return (T) data;
            }
            case CvType.CV_32S: {
                final int[] data = new int[rows * cols * channels];
                m.get(0, 0, data);
                return (T) data;
            }
            case CvType.CV_32F: {
                final float[] data = new float[rows * cols * channels];
                m.get(0, 0, data);
                return (T) data;
            }
            case CvType.CV_64F: {
                final double[] data = new double[rows * cols * channels];
                m.get(0, 0, data);
                return (T) data;
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
        switch (CvType.depth(raster.type())) {
            case CvType.CV_8S:
                return (p, ch, chv) -> ((byte[]) p)[ch] = (byte) ((chv > Byte.MAX_VALUE) ? Byte.MAX_VALUE : chv);
            case CvType.CV_8U:
                return (p, ch, chv) -> ((byte[]) p)[ch] = (byte) ((chv > 0xFF) ? 0xFF : chv);
            case CvType.CV_16S:
                return (p, ch, chv) -> ((short[]) p)[ch] = (short) ((chv > Short.MAX_VALUE) ? Short.MAX_VALUE : chv);
            case CvType.CV_16U:
                return (p, ch, chv) -> ((short[]) p)[ch] = (short) ((chv > 0xFFFF) ? 0XFFFF : chv);
            case CvType.CV_32S:
                return (p, ch, chv) -> ((int[]) p)[ch] = chv;
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type()));
        }
    }

    public static GetChannelValueAsInt channelValueFetcher(final CvRaster raster) {
        switch (CvType.depth(raster.type())) {
            case CvType.CV_8S:
                return (p, ch) -> (int) ((byte[]) p)[ch];
            case CvType.CV_8U:
                return (p, ch) -> (((byte[]) p)[ch] & 0xFF);
            case CvType.CV_16S:
                return (p, ch) -> (int) ((short[]) p)[ch];
            case CvType.CV_16U:
                return (p, ch) -> (((short[]) p)[ch] & 0xFFFF);
            case CvType.CV_32S:
                return (p, ch) -> ((int[]) p)[ch];
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type()));
        }
    }

    public static PixelToInts pixelToIntsConverter(final CvRaster raster) {
        final GetChannelValueAsInt fetcher = channelValueFetcher(raster);
        final int numChannels = raster.channels();
        final int[] ret = new int[numChannels];
        return (p -> {
            for (int i = 0; i < numChannels; i++)
                ret[i] = fetcher.get(p, i);
            return ret;
        });
    }

    public static int numChannelElementValues(final CvRaster raster) {
        switch (CvType.depth(raster.type())) {
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
        final int channels = raster.channels();
        switch (CvType.depth(raster.type())) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return new byte[channels];
            case CvType.CV_16S:
            case CvType.CV_16U:
                return new short[channels];
            case CvType.CV_32S:
                return new int[channels];
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type()));

        }
    }

    public static IntsToPixel intsToPixelConverter(final CvRaster raster) {
        final PutChannelValueFromInt putter = channelValuePutter(raster);
        final int numChannels = raster.channels();
        final Object pixel = makePixel(raster);
        return ints -> {
            for (int i = 0; i < numChannels; i++)
                putter.put(pixel, i, ints[i]);
            return pixel;
        };
    }

    @FunctionalInterface
    public static interface PixelVisitor<T> {
        public T apply(Object pixel);
    }

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

        public <T extends AutoCloseable> T add(final T mat) {
            if (mat != null)
                rastersToClose.add(0, mat);
            return mat;
        }

        @Override
        public void close() {
            rastersToClose.stream().forEach(
                    r -> {
                        try {
                            r.close();
                        } catch (final Exception e) {
                            // impossible
                        }
                    });
        }

        public void release() {
            rastersToClose.clear();
        }
    }

    @FunctionalInterface
    public static interface PixelAggregate<P, R> {
        R apply(R prev, P pixel, int row, int col);
    }

    private static ByteBuffer getData(final Mat mat) {
        final ByteBuffer ret = _getData(mat);
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
            ret.order(ByteOrder.LITTLE_ENDIAN);
        return ret;
    }

}
