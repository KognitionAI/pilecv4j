package com.jiminger.image;

import java.util.function.Function;
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

    public static CvRaster create(final int rows, final int cols, final int type) {
        final int channels = CvType.channels(type);
        final int depth = CvType.depth(type);

        switch (depth) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return new CvRaster(new byte[rows * cols * channels], type, channels, rows, cols) {
                    final byte[] d = ((byte[]) data);

                    @Override
                    public void zero(final int row, final int col) {
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> d[pos + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (byte[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final byte[] ret = new byte[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ret[i] = d[pos + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final byte[] p = (byte[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> d[pos + i] = p[i]);
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
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((short[]) data)[pos + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (short[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final short[] ret = new short[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((short[]) data)[pos + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final short[] p = (short[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((short[]) data)[pos + i] = p[i]);
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
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((int[]) data)[pos + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (int[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final int[] ret = new int[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((int[]) data)[pos + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final int[] p = (int[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((int[]) data)[pos + i] = p[i]);
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
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((float[]) data)[pos + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (float[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final float[] ret = new float[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((float[]) data)[pos + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final float[] p = (float[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((float[]) data)[pos + i] = p[i]);
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
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((double[]) data)[pos + i] = 0);
                    }

                    @Override
                    public void loadFrom(final Mat image) {
                        image.get(0, 0, (double[]) data);
                    }

                    @Override
                    public Object get(final int row, final int col) {
                        final double[] ret = new double[channels];
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ret[i] = ((double[]) data)[pos + i]);
                        return ret;
                    }

                    @Override
                    public void set(final int row, final int col, final Object pixel) {
                        final double[] p = (double[]) pixel;
                        final int pos = (row * colsXchannels) + (col * channels);
                        IntStream.range(0, channels).forEach(i -> ((double[]) data)[pos + i] = p[i]);
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
