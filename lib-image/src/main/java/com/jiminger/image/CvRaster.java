package com.jiminger.image;

import java.util.stream.IntStream;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

public abstract class CvRaster {
    public final Object data;
    public final int type;
    public final int channels;
    public final int rows;
    public final int cols;
    public final int elemSize;
    protected final int colsXchannels;

    private CvRaster(final Object data, final int type, final int channels, final int rows, final int cols) {
        this.data = data;
        this.type = type;
        this.channels = channels;
        this.rows = rows;
        this.cols = cols;
        this.elemSize = CvType.ELEM_SIZE(type);
        this.colsXchannels = cols * channels;
    }

    public static CvRaster create(final int rows, final int cols, final int type) {
        final int channels = CvType.channels(type);

        switch (CvType.depth(type)) {
            case CvType.CV_8U:
            case CvType.CV_8S:
                return new CvRaster(new byte[rows * cols * channels], type, channels, rows, cols) {
                    final byte[] d = ((byte[]) data);

                    @Override
                    public void zero(final int row, final int col) {
                        IntStream.range(0, channels).forEach(i -> d[((row * colsXchannels) + (col * channels)) + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (byte[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final byte[] ret = new byte[channels];
                        IntStream.range(0, channels).forEach(i -> ret[i] = d[((row * colsXchannels) + (col * channels)) + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final byte[] p = (byte[]) pixel;
                        IntStream.range(0, channels).forEach(i -> d[((row * colsXchannels) + (col * channels)) + i] = p[i]);
                    }

                    @Override
                    public Mat toMat() {
                        final Mat ret = new Mat(rows, cols, type);
                        ret.put(0, 0, (byte[]) data);
                        return ret;
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final BytePixelSetter bps = (BytePixelSetter) ps;
                        final byte[] d = (byte[]) data;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                final byte[] pixel = bps.pixel(row, col);
                                final int pixPos = rowOffset + (col * channels);
                                for (int band = 0; band < channels; band++)
                                    d[pixPos + band] = pixel[band];
                            }
                        }
                    }
                };
            case CvType.CV_16U:
            case CvType.CV_16S:
                return new CvRaster(new short[rows * cols * channels], type, channels, rows, cols) {
                    @Override
                    public void zero(final int row, final int col) {
                        IntStream.range(0, channels).forEach(i -> ((short[]) data)[((row * colsXchannels) + (col * channels)) + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (short[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final short[] ret = new short[channels];
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((short[]) data)[((row * colsXchannels) + (col * channels)) + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final short[] p = (short[]) pixel;
                        IntStream.range(0, channels).forEach(i -> ((short[]) data)[((row * colsXchannels) + (col * channels)) + i] = p[i]);
                    }

                    @Override
                    public Mat toMat() {
                        final Mat ret = new Mat(rows, cols, type);
                        ret.put(0, 0, (short[]) data);
                        return ret;
                    }

                    @Override
                    public <T> void apply(final PixelSetter<T> ps) {
                        final ShortPixelSetter bps = (ShortPixelSetter) ps;
                        final short[] d = (short[]) data;
                        for (int row = 0; row < rows; row++) {
                            final int rowOffset = row * colsXchannels;
                            for (int col = 0; col < cols; col++) {
                                final short[] pixel = bps.pixel(row, col);
                                final int pixPos = rowOffset + (col * channels);
                                for (int band = 0; band < channels; band++)
                                    d[pixPos + band] = pixel[band];
                            }
                        }
                    }
                };
            case CvType.CV_32S:
                return new CvRaster(new int[rows * cols * channels], type, channels, rows, cols) {
                    @Override
                    public void zero(final int row, final int col) {
                        IntStream.range(0, channels).forEach(i -> ((int[]) data)[((row * colsXchannels) + (col * channels)) + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (int[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final int[] ret = new int[channels];
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((int[]) data)[((row * colsXchannels) + (col * channels)) + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final int[] p = (int[]) pixel;
                        IntStream.range(0, channels).forEach(i -> ((int[]) data)[((row * colsXchannels) + (col * channels)) + i] = p[i]);
                    }

                    @Override
                    public Mat toMat() {
                        final Mat ret = new Mat(rows, cols, type);
                        ret.put(0, 0, (int[]) data);
                        return ret;
                    }
                };
            case CvType.CV_32F:
                return new CvRaster(new float[rows * cols * channels], type, channels, rows, cols) {
                    @Override
                    public void zero(final int row, final int col) {
                        IntStream.range(0, channels).forEach(i -> ((float[]) data)[((row * colsXchannels) + (col * channels)) + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (float[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final float[] ret = new float[channels];
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((float[]) data)[((row * colsXchannels) + (col * channels)) + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final float[] p = (float[]) pixel;
                        IntStream.range(0, channels).forEach(i -> ((float[]) data)[((row * colsXchannels) + (col * channels)) + i] = p[i]);
                    }

                    @Override
                    public Mat toMat() {
                        final Mat ret = new Mat(rows, cols, type);
                        ret.put(0, 0, (float[]) data);
                        return ret;
                    }
                };
            case CvType.CV_64F:
                return new CvRaster(new double[rows * cols * channels], type, channels, rows, cols) {
                    @Override
                    public void zero(final int row, final int col) {
                        IntStream.range(0, channels).forEach(i -> ((double[]) data)[((row * colsXchannels) + (col * channels)) + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (double[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final double[] ret = new double[channels];
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((double[]) data)[((row * colsXchannels) + (col * channels)) + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final double[] p = (double[]) pixel;
                        IntStream.range(0, channels).forEach(i -> ((double[]) data)[((row * colsXchannels) + (col * channels)) + i] = p[i]);
                    }

                    @Override
                    public Mat toMat() {
                        final Mat ret = new Mat(rows, cols, type);
                        ret.put(0, 0, (double[]) data);
                        return ret;
                    }
                };
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
        }
    }

    public static CvRaster create(final Mat image) {
        final CvRaster ret = create(image.height(), image.width(), image.type());
        ret.loadFrom(image);
        return ret;
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

    public abstract void zero(int row, int col);

    public abstract void loadFrom(Mat image);

    public abstract Object get(int row, int col);

    public abstract void set(int row, int col, Object pixel);

    public abstract Mat toMat();

    public <T> void apply(final PixelSetter<T> pixelSetter) {
        throw new UnsupportedOperationException();
    }

    @FunctionalInterface
    public static interface PixelAggregate<P, R> {
        R apply(R prev, P pixel, int row, int col);
    }

    public <U> U reduce(final U identity, final PixelAggregate<Object, U> seqOp) {
        U prev = identity;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                prev = seqOp.apply(prev, get(r, c), r, c);
            }
        }
        return prev;
    }
}
