package com.jiminger.image;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.function.Function;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * <p>
 * This class is an easier (perhaps) and more efficient interface to an OpenCV Mat. To use it you as CvRaster to "manage" a {@code Mat}. Using the {@code Mat} through the CvRaster will then operate directly on
 * the image data in the {@code Mat} in a "zero copy" form with a few notable exceptions. When the CvRaster is closed, the underlying mat will be "released."
 * </p>
 * 
 * <p>
 * To obtain a CvRaster you use the {@link CvRaster#manage(Mat)} as follows.
 * </p>
 * 
 * <pre>
 * <code>
 * try (CvRaster raster = CvRaster.manage(mat);) {
 *      // do something with the raster
 * } // raster closed and Mat released
 * </code>
 * </pre>
 */
public abstract class CvRaster implements AutoCloseable {
    // public final Object data;
    public final int type;
    public final int channels;
    public final int rows;
    public final int cols;
    public final int elemSize;
    protected final int colsXchannels;
    protected final ByteBuffer bb;
    private final Mat mat;

    private CvRaster(final Mat m, final ByteBuffer bb) {
        this.rows = m.rows();
        this.cols = m.cols();
        this.type = m.type();
        this.channels = CvType.channels(type);
        this.elemSize = CvType.ELEM_SIZE(type);
        this.colsXchannels = cols * channels;
        this.mat = m;
        this.bb = bb;
    }

    public static CvRaster manage(final Mat mat) {
        return makeInstance(mat, getData(mat));
    }

    public static CvRaster copyFrom(final Mat mat) {
        return makeInstance(mat, copyToPrimitiveArray(mat));
    }

    private static CvRaster makeInstance(final Mat mat, final ByteBuffer bb) {
        bb.clear();
        final int type = mat.type();
        final int depth = CvType.depth(type);

        switch (depth) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return new CvRaster(mat, bb) {

                    @Override
                    public void zero(final int row, final int col) {
                        final byte[] zeroPixel = new byte[channels]; // zeroed already
                        apply((BytePixelSetter) (r, c) -> zeroPixel);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final byte[] ret = new byte[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        bb.position(pos);
                        bb.get(ret);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final byte[] p = (byte[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        bb.position(pos);
                        bb.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final BytePixelSetter bps = (BytePixelSetter) ps;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                bb.position(rowOffset + (col * channels));
                                bb.put(bps.pixel(row, col));
                            }
                        }
                    }

                };
            case CvType.CV_16U:
            case CvType.CV_16S:
                return new CvRaster(mat, bb) {
                    final ShortBuffer sb = bb.asShortBuffer();

                    @Override
                    public void zero(final int row, final int col) {
                        final short[] zeroPixel = new short[channels]; // zeroed already
                        apply((ShortPixelSetter) (r, c) -> zeroPixel);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final short[] ret = new short[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        sb.position(pos);
                        sb.get(ret);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final short[] p = (short[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        sb.position(pos);
                        sb.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final ShortPixelSetter bps = (ShortPixelSetter) ps;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                sb.position(rowOffset + (col * channels));
                                sb.put(bps.pixel(row, col));
                            }
                        }
                    }
                };
            case CvType.CV_32S:
                return new CvRaster(mat, bb) {
                    final IntBuffer ib = bb.asIntBuffer();

                    @Override
                    public void zero(final int row, final int col) {
                        final int[] zeroPixel = new int[channels]; // zeroed already
                        apply((IntPixelSetter) (r, c) -> zeroPixel);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final int[] ret = new int[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        ib.position(pos);
                        ib.get(ret);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final int[] p = (int[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        ib.position(pos);
                        ib.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final IntPixelSetter bps = (IntPixelSetter) ps;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                ib.position(rowOffset + (col * channels));
                                ib.put(bps.pixel(row, col));
                            }
                        }
                    }
                };
            case CvType.CV_32F:
                return new CvRaster(mat, bb) {
                    final FloatBuffer fb = bb.asFloatBuffer();

                    @Override
                    public void zero(final int row, final int col) {
                        final float[] zeroPixel = new float[channels]; // zeroed already
                        apply((FloatPixelSetter) (r, c) -> zeroPixel);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final float[] ret = new float[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        fb.position(pos);
                        fb.get(ret);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final float[] p = (float[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        fb.position(pos);
                        fb.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final FloatPixelSetter bps = (FloatPixelSetter) ps;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                fb.position(rowOffset + (col * channels));
                                fb.put(bps.pixel(row, col));
                            }
                        }
                    }
                };
            case CvType.CV_64F:
                return new CvRaster(mat, bb) {
                    final DoubleBuffer db = bb.asDoubleBuffer();

                    @Override
                    public void zero(final int row, final int col) {
                        final double[] zeroPixel = new double[channels]; // zeroed already
                        apply((DoublePixelSetter) (r, c) -> zeroPixel);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final double[] ret = new double[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        db.position(pos);
                        db.get(ret);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final double[] p = (double[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        db.position(pos);
                        db.put(p);
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final DoublePixelSetter bps = (DoublePixelSetter) ps;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                db.position(rowOffset + (col * channels));
                                db.put(bps.pixel(row, col));
                            }
                        }
                    }
                };
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
        }
    }

    public abstract void zero(int row, int col);

    public abstract Object get(int row, int col);

    public abstract void set(int row, int col, Object pixel);

    public <U> U reduce(final U identity, final PixelAggregate<Object, U> seqOp) {
        U prev = identity;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                prev = seqOp.apply(prev, get(r, c), r, c);
            }
        }
        return prev;
    }

    public <T> void apply(final PixelSetter<T> pixelSetter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        if (mat != null && bb.isDirect()) // if the bb is not direct then we're no managing the mat
            mat.release();
    }

    public static ByteBuffer copyToIndirectByteBuffer(final Mat m) {
        final byte[] data = new byte[m.rows() * m.cols() * CvType.ELEM_SIZE(m.type())];
        DoubleBuffer.w
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
        switch (CvType.depth(raster.type)) {
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
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type));
        }
    }

    public static GetChannelValueAsInt channelValueFetcher(final CvRaster raster) {
        switch (CvType.depth(raster.type)) {
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
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type));
        }
    }

    public static PixelToInts pixelToIntsConverter(final CvRaster raster) {
        final GetChannelValueAsInt fetcher = channelValueFetcher(raster);
        final int numChannels = raster.channels;
        final int[] ret = new int[numChannels];
        return (p -> {
            for (int i = 0; i < numChannels; i++)
                ret[i] = fetcher.get(p, i);
            return ret;
        });
    }

    public static int numChannelElementValues(final CvRaster raster) {
        switch (CvType.depth(raster.type)) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return 256;
            case CvType.CV_16S:
            case CvType.CV_16U:
                return 65536;
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type));
        }
    }

    public static Object makePixel(final CvRaster raster) {
        final int channels = raster.channels;
        switch (CvType.depth(raster.type)) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return new byte[channels];
            case CvType.CV_16S:
            case CvType.CV_16U:
                return new short[channels];
            case CvType.CV_32S:
                return new int[channels];
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(raster.type));

        }
    }

    public static IntsToPixel intsToPixelConverter(final CvRaster raster) {
        final PutChannelValueFromInt putter = channelValuePutter(raster);
        final int numChannels = raster.channels;
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

    @FunctionalInterface
    public static interface PixelAggregate<P, R> {
        R apply(R prev, P pixel, int row, int col);
    }

    private static ByteBuffer getData(final Mat mat) {
        return CvRasterNative._getData(mat.nativeObj);
    }
}
