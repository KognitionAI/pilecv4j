package ai.kognition.pilecv4j.image;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_CUSTOM;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;
import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;
import static org.opencv.core.CvType.CV_16S;
import static org.opencv.core.CvType.CV_16U;
import static org.opencv.core.CvType.CV_32S;
import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8U;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.Closer;
import ai.kognition.pilecv4j.image.CvRaster.DoublePixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.DoublePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.FlatDoublePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.FlatFloatPixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.FloatPixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.FloatPixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.IntPixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.PixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.ShortPixelConsumer;
import ai.kognition.pilecv4j.image.geometry.PerpendicularLine;
import ai.kognition.pilecv4j.image.geometry.Point;
import ai.kognition.pilecv4j.image.geometry.SimplePoint;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

public class Utils {
    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    /**
     * This is set through reflection because the source changed between OpenCV 3
     * and OpenCV 4.
     */
    public final static int OCV_FONT_HERSHEY_SIMPLEX;

    private final static Method OCV_UNDISTORT_METHOD;
    private final static Method OCV_UNDISTORT_POINTS_METHOD;
    private final static CvMat bgra2abgr = Utils.toMat(new float[][] {
        {0,1,0,0},
        {0,0,1,0},
        {0,0,0,1},
        {1,0,0,0}
    });

    private final static CvMat abgr2bgra = Utils.toMat(new float[][] {
        {0,0,0,1},
        {1,0,0,0},
        {0,1,0,0},
        {0,0,1,0}
    });

    private static final int NUM_DEPTH_CONSTS = 8;
    private static final int[] BITS_PER_CHANNEL_LOOKUP = new int[NUM_DEPTH_CONSTS];

    // Dynamically determine if we're at major version 3 or 4 of OpenCV and set the
    // variables appropriately.
    static {
        // this 3.x.x uses Core while 4.x.x uses Imgproc
        OCV_FONT_HERSHEY_SIMPLEX = uncheck(() -> Integer.valueOf(getStaticField("FONT_HERSHEY_SIMPLEX", Core.class, Imgproc.class).getInt(null))).intValue();
        OCV_UNDISTORT_METHOD = getStaticMethod("undistort", new Class<?>[] {Mat.class,Mat.class,Mat.class,Mat.class}, Imgproc.class, Calib3d.class);
        OCV_UNDISTORT_POINTS_METHOD = getStaticMethod("undistortPoints",
                new Class<?>[] {MatOfPoint2f.class,MatOfPoint2f.class,Mat.class,Mat.class,Mat.class,Mat.class},
                Imgproc.class, Calib3d.class);
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_8S] = 8;
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_8U] = 8;
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_16S] = 16;
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_16U] = 16;
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_32S] = 32;
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_32F] = 32;
        BITS_PER_CHANNEL_LOOKUP[CvType.CV_64F] = 64;
    }

    /**
     * Given the CvType, how many bits-per-channel. For example
     * {@code CvType.CV_8UC1}, {@code CvType.CV_8UC2}, and
     * {@code CvType.CV_8UC3} will all return {@code 8}.
     */
    public static int bitsPerChannel(final int type) {
        final int depth = CvType.depth(type);
        if(depth > (NUM_DEPTH_CONSTS - 1))
            throw new IllegalStateException(
                    "Something in OpenCV is no longer what it used to be. Depth constants are 3 bits and so should never be greater than "
                            + (NUM_DEPTH_CONSTS - 1)
                            + ". However, for type " + CvType.typeToString(type) + " it seems to be " + depth);
        final int ret = BITS_PER_CHANNEL_LOOKUP[depth];
        if(ret <= 0)
            throw new IllegalArgumentException(
                    "The type " + CvType.typeToString(type) + ", resulting in a depth constant of " + depth
                            + " has no corresponding bit-per-channel value");
        return ret;
    }

    /**
     * This method simply proxies a call to OpenCV's
     * <a href=
     * "https://docs.opencv.org/4.0.1/d9/d0c/group__calib3d.html#ga69f2545a8b62a6b0fc2ee060dc30559d">undistort</a>
     * method in order to provide compatibility between OpenCV 3 and OpenCV 4 when
     * it was moved from
     * {@code Imgproc} to {@code Calib3d}.
     */
    public static void undistort(final Mat src, final Mat dst, final Mat cameraMatrix, final Mat distCoeffs) {
        try {
            OCV_UNDISTORT_METHOD.invoke(null, src, dst, cameraMatrix, distCoeffs);
        } catch(final IllegalAccessException e) {
            throw new IllegalStateException("The method " + OCV_UNDISTORT_METHOD.getName() + " isn't accessible.", e);
        } catch(final InvocationTargetException e) {
            throw(RuntimeException)e.getCause();
        }
    }

    /**
     * This method simply proxies a call to OpenCV's
     * <a href=
     * "https://docs.opencv.org/4.0.1/d9/d0c/group__calib3d.html#ga69f2545a8b62a6b0fc2ee060dc30559d">undistortPoints</a>
     * method in order to provide compatibility between OpenCV 3 and OpenCV 4 when
     * it was moved from
     * {@code Imgproc} to {@code Calib3d}.
     */
    public static void undistortPoints(final MatOfPoint2f src, final MatOfPoint2f dst, final Mat cameraMatrix, final Mat distCoeffs, final Mat R,
            final Mat P) {
        try {
            OCV_UNDISTORT_POINTS_METHOD.invoke(null, src, dst, cameraMatrix, distCoeffs, R, P);
        } catch(final IllegalAccessException e) {
            throw new IllegalStateException("The method " + OCV_UNDISTORT_METHOD.getName() + " isn't accessible.", e);
        } catch(final InvocationTargetException e) {
            throw(RuntimeException)e.getCause();
        }
    }

    /**
     * <p>
     * Convert an OpenCV
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     * (or a {@link CvMat}) to a {@link BufferedImage} that can be used in java
     * swing and awt. Currently this can handle:
     * </p>
     * <ul>
     * <li>A single channel grayscale image of 8 or 16 bits.</li>
     * <li>A 3 channel color image of 8-bits per channel.*</li>
     * <li>A 4 channel color image with an alpha channel of 8-bits per
     * channel.*</li>
     * </ul>
     *
     * <p>
     * <em>* Note: the method assumes color
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat's</a>
     * are in typical OpenCV BGR (or, for 4 channel images aBGR) format.</em>
     * </p>
     *
     * <p>
     * 8-bit per channel color images will be transformed to {@link BufferedImage}s
     * of type {@link BufferedImage#TYPE_3BYTE_BGR} for 3 channel images and
     * {@link BufferedImage#TYPE_4BYTE_ABGR} for 4 channel images.
     * </p>
     *
     * TODO: 16-bit per channel color images
     *
     * @param in <a href=
     *            "https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     *            to be converted
     * @return new {@link BufferedImage} from the <a href=
     *         "https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     */
    public static BufferedImage mat2Img(final Mat in) {
        final int inChannels = in.channels();
        if(inChannels == 1) { // assume gray
            final int type;

            final Function<BufferedImage, Object> toRawData;

            switch(CvType.depth(in.type())) {
                case CV_8U:
                case CV_8S:
                    type = BufferedImage.TYPE_BYTE_GRAY;
                    toRawData = bi -> ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
                    break;
                case CV_16U:
                case CV_16S:
                    type = BufferedImage.TYPE_USHORT_GRAY;
                    toRawData = bi -> ((DataBufferUShort)bi.getRaster().getDataBuffer()).getData();
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Cannot convert a Mat with a type of " + CvType.typeToString(in.type()) + " to a BufferedImage");
            }

            final BufferedImage out = new BufferedImage(in.width(), in.height(), type);
            CvRaster.copyToPrimitiveArray(in, toRawData.apply(out));
            return out;
        } else if(inChannels == 3) {
            final int cvDepth = CvType.depth(in.type());
            if(cvDepth != CV_8U && cvDepth != CV_8S)
                throw new IllegalArgumentException("Cannot convert BGR Mats with elements larger than a byte yet.");

            final BufferedImage out = new BufferedImage(in.width(), in.height(), BufferedImage.TYPE_3BYTE_BGR);
            CvRaster.copyToPrimitiveArray(in, ((DataBufferByte)out.getRaster().getDataBuffer()).getData());
            return out;
        } else if(inChannels == 4) { // assumption here is we have a BGRA
            final int cvDepth = CvType.depth(in.type());
            if(cvDepth != CV_8U && cvDepth != CV_8S)
                throw new IllegalArgumentException("Cannot convert aBGR Mats with elements larger than a byte yet.");
            final BufferedImage out = new BufferedImage(in.width(), in.height(), BufferedImage.TYPE_4BYTE_ABGR);
            final int height = in.rows();
            final int width = in.cols();

            // flatten so every pixel is a separate row
            try (final CvMat reshaped = CvMat.move(in.reshape(1, height * width));
                    // type to 32F
                    final CvMat typed = Functional.chain(new CvMat(), m -> reshaped.convertTo(m, CvType.CV_32F));
                    // color transform which just reorganizes the pixels.
                    final CvMat xformed = typed.mm(bgra2abgr);
                    final CvMat xformedAndShaped = CvMat.move(xformed.reshape(4, height));
                    final CvMat it = Functional.chain(new CvMat(), m -> xformedAndShaped.convertTo(m, cvDepth));) {
                CvRaster.copyToPrimitiveArray(it, ((DataBufferByte)out.getRaster().getDataBuffer()).getData());
                return out;
            }
        } else
            throw new IllegalArgumentException("Can't handle an image with " + inChannels + " channels");
    }

    /**
     * <p>
     * Convert an OpenCV
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     * (or a {@link CvMat}) to a {@link BufferedImage} that can be used in java
     * swing and awt using a specific index color model (see
     * {@link IndexColorModel}).
     * </p>
     *
     * <p>
     * This is a much simpler implementation that {@link Utils#mat2Img(Mat)} in that
     * it only handles a 1-channel, 8-bit image but allows you to assign colors to
     * each of the 256 values. This is primarily used to generated overlays on other
     * images and represents a "poor-man's" alpha channel manipulation in OpenCV
     * which doesn't really have much in the way of alpha channel handling natively.
     * </p>
     */
    public static BufferedImage mat2Img(final Mat in, final IndexColorModel colorModel) {
        BufferedImage out;

        if(in.channels() != 1 || CvType.depth(in.type()) != CvType.CV_8U)
            throw new IllegalArgumentException("Cannot convert a Mat to a BufferedImage with a colorMap if the Mat has more than one channel);");

        out = new BufferedImage(in.cols(), in.rows(), BufferedImage.TYPE_BYTE_INDEXED, colorModel);

        out.getRaster().setDataElements(0, 0, in.cols(), in.rows(), CvRaster.copyToPrimitiveArray(in));
        return out;
    }

    /**
     * This is a convenience method for {@link Utils#dump(Mat, PrintStream)} that
     * uses {@link System#out} as the {@link PrintStream}
     */
    public static void dump(final Mat mat, final int numRows, final int numCols) {
        dump(mat, System.out, numRows, numCols);
    }

    /**
     * This is a convenience method for
     * {@link Utils#dump(Mat, PrintStream, int, int)} that uses {@link System#out}
     * as the {@link PrintStream} and dumps all elements of the
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     */
    public static void dump(final Mat mat) {
        dump(mat, System.out, -1, -1);
    }

    /**
     * This is a convenience method for {@link Utils#dump(Mat, PrintStream,int,int)}
     * that dumps all elements of the
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     */
    public static void dump(final Mat mat, final PrintStream out) {
        dump(mat, out, -1, -1);
    }

    /**
     * You can use this method to dump the contents of a {@link CvRaster} to the
     * {@link PrintStream}.
     * Please note this is a really bad idea for large images but can help with
     * debugging problems when
     * you're using OpenCV for it's linear-algebra/matrix capabilities.
     *
     * @param mat to dump print to the {@link PrintStream}
     * @param out is the {@link PrintStream} to dump the {@link CvRaster} to.
     * @param numRows limit the number of rows to the given number. Supply -1 for
     *            the all rows.
     * @param numCols limit the number of columns to the given number. Supply -1 for
     *            the all columns.
     */
    public static void dump(final Mat mat, final PrintStream out, final int numRows, final int numCols) {
        CvMat.rasterAp(mat, raster -> dump(raster, out, numRows, numCols));
    }

    private static String arrayToHexString(final Function<Integer, Long> valueGetter, final int length, final long mask) {
        final StringBuilder sb = new StringBuilder("[");
        IntStream.range(0, length - 1)
                .forEach(i -> {
                    sb.append(Long.toHexString(valueGetter.apply(i) & mask));
                    sb.append(", ");
                });

        if(length > 0)
            sb.append(Long.toHexString(valueGetter.apply(length - 1) & mask));
        sb.append("]");
        return sb.toString();
    }

    private static PixelConsumer<?> makePixelPrinter(final PrintStream stream, final int type) {
        switch(CvType.depth(type)) {
            case CvType.CV_8S:
            case CvType.CV_8U:
                return (BytePixelConsumer)(final int r, final int c, final byte[] pixel) -> stream
                        .print(arrayToHexString(i -> (long)pixel[i], pixel.length, 0xffL));
            case CvType.CV_16S:
            case CvType.CV_16U:
                return (ShortPixelConsumer)(final int r, final int c, final short[] pixel) -> stream
                        .print(arrayToHexString(i -> (long)pixel[i], pixel.length, 0xffffL));
            case CvType.CV_32S:
                return (IntPixelConsumer)(final int r, final int c, final int[] pixel) -> stream
                        .print(arrayToHexString(i -> (long)pixel[i], pixel.length, 0xffffffffL));
            case CvType.CV_32F:
                return (FloatPixelConsumer)(final int r, final int c, final float[] pixel) -> stream.print(Arrays.toString(pixel));
            case CvType.CV_64F:
                return (DoublePixelConsumer)(final int r, final int c, final double[] pixel) -> stream.print(Arrays.toString(pixel));
            default:
                throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
        }
    }

    /**
     * You can use this method to dump the contents of a
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> to
     * the {@link PrintStream}.
     * Please note this is a really bad idea for large images but can help with
     * debugging problems when
     * you're using OpenCV for it's linear-algebra/matrix capabilities.
     *
     * @param raster to dump print to the {@link PrintStream}
     * @param out is the {@link PrintStream} to dump the {@link CvRaster} to.
     * @param numRows limit the number of rows to the given number. Supply -1 for
     *            the all rows.
     * @param numCols limit the number of columns to the given number. Supply -1 for
     *            the all columns.
     */
    @SuppressWarnings("unchecked")
    private static void dump(final CvRaster raster, final PrintStream out, int numRows, int numCols) {
        if(numRows < 0) numRows = raster.rows();
        if(numCols < 0) numCols = raster.cols();

        if(raster.rows() < numRows)
            numRows = raster.rows();
        if(raster.cols() < numCols)
            numCols = raster.cols();

        out.println(raster.mat);
        @SuppressWarnings("rawtypes")
        final PixelConsumer pp = makePixelPrinter(out, raster.type());
        for(int r = 0; r < numRows; r++) {
            out.print("[");
            for(int c = 0; c < numCols - 1; c++) {
                out.print(" ");
                pp.accept(r, c, raster.get(r, c));
                out.print(",");
            }
            out.print(" ");
            pp.accept(r, numCols - 1, raster.get(r, numCols - 1));
            out.println("]");
        }
    }

    private static int[] determineShifts(final int[] masks) {
        final int[] ret = new int[masks.length];
        for(int i = 0; i < masks.length; i++) {
            int mask = masks[i];
            int shift = 0;
            if(mask != 0) {
                while((mask & 1) == 0) {
                    mask >>>= 1;
                    shift++;
                }
            }
            ret[i] = shift;
        }
        return ret;
    }

    /**
     * set the mask to a scalar = 1 channel 1x1 Mat the same type as the rawMat
     */
    private static void makeScalarMat(final int mask, final int type, final CvMat toSet) {
        CvMat.reassign(toSet, new CvMat(1, 1, type));
        final Object maskPixel = CvRaster.intsToPixelConverter(type).apply(new int[] {mask});
        toSet.rasterAp(r -> r.set(0, maskPixel));
    }

    private static CvMat handleComponentColorModel(final BufferedImage bufferedImage, final ComponentColorModel cm) {
        final int w = bufferedImage.getWidth();
        final int h = bufferedImage.getHeight();
        final int type = bufferedImage.getType();
        switch(type) {
            case TYPE_CUSTOM:
                return handleCustomComponentColorModel(bufferedImage, cm);
            case TYPE_3BYTE_BGR:
            case TYPE_4BYTE_ABGR:
            case TYPE_4BYTE_ABGR_PRE: {
                System.out.println("NORMAL COPY");
                final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
                if(!(dataBuffer instanceof DataBufferByte))
                    throw new IllegalArgumentException("BufferedImage of type \"" + type + "\" should have a " + DataBufferByte.class.getSimpleName()
                            + " but instead has a " + dataBuffer.getClass().getSimpleName());
                final DataBufferByte bb = (DataBufferByte)dataBuffer;
                switch(type) {
                    case TYPE_3BYTE_BGR:
                        return abgrDataBufferByteToMat(bb, h, w, false);
                    case TYPE_4BYTE_ABGR:
                    case TYPE_4BYTE_ABGR_PRE:
                        return abgrDataBufferByteToMat(bb, h, w, true);
                }

            }
            case TYPE_BYTE_GRAY: {
                System.out.println("GRAY");
                final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
                if(!(dataBuffer instanceof DataBufferByte))
                    throw new IllegalArgumentException("BufferedImage should have a " + DataBufferByte.class.getSimpleName() + " but instead has a "
                            + dataBuffer.getClass().getSimpleName());
                final DataBufferByte bb = (DataBufferByte)dataBuffer;
                final byte[] srcdata = bb.getData();
                try (final CvMat ret = new CvMat(h, w, CvType.CV_8UC1);) {
                    ret.put(0, 0, srcdata);
                    return ret.returnMe();
                }
            }
            case TYPE_USHORT_GRAY: {
                System.out.println("GRAY 16");
                final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
                if(!(dataBuffer instanceof DataBufferUShort))
                    throw new IllegalArgumentException("BufferedImage should have a " + DataBufferUShort.class.getSimpleName() + " but instead has a "
                            + dataBuffer.getClass().getSimpleName());
                final DataBufferUShort bb = (DataBufferUShort)dataBuffer;
                final short[] srcdata = bb.getData();
                try (final CvMat ret = new CvMat(h, w, CvType.CV_16UC1);) {
                    ret.put(0, 0, srcdata);
                    return ret.returnMe();
                }
            }
            default:
                throw new IllegalArgumentException("Cannot extract pixels from a BufferedImage of type " + bufferedImage.getType());

        }
    }

    // The packed pixels are ARGB. But the transform expects the masks ordered RGBA
    // private static final int[] stdARGBMasks = {0x00ff0000,0x0000ff00,0x000000ff,0xff000000};
    // private static final int[] stdRGBMasks = {0x00ff0000,0x0000ff00,0x000000ff};

    // private static CvMat fallback(final BufferedImage bufferedImage, final ColorModel cm) {
    // System.out.println("FALLBACK!!!!!");
    // LOGGER.trace("Needed to fall back. This will be much slower");
    // final int w = bufferedImage.getWidth();
    // final int h = bufferedImage.getHeight();
    //
    // final int[] rgbs = bufferedImage.getRGB(0, 0, w, h, null, 0, w);
    // try (CvMat mat = new CvMat(h, w, CvType.CV_32SC1);) {
    // mat.put(0, 0, rgbs);
    // final boolean hasAlpha = cm.hasAlpha();
    // return transformDirect(mat, hasAlpha, cm.isAlphaPremultiplied(), hasAlpha ? stdARGBMasks : stdRGBMasks);
    // }
    // }

    /**
     * determine the number of bits for each channel. and set the flag that
     * indicates whether or not they're all the same.
     */
    private static int[] ccmCeckBitsPerChannel(final ComponentColorModel cm) {
        final int[] bitsPerChannel = cm.getComponentSize();
        final int bpc = bitsPerChannel[0];
        if(IntStream.range(1, bitsPerChannel.length)
                .filter(i -> bitsPerChannel[i] != bpc)
                .findAny()
                .isPresent())
            throw new IllegalArgumentException(
                    "Cannot handle an image with ComponentColorModel where the channels have a different number of bits per channel. They are currently: "
                            + Arrays.toString(bitsPerChannel));
        return bitsPerChannel;
    }

    private static CvMat handleCMYKColorSpace(final BufferedImage bufferedImage, final ComponentColorModel cm, final boolean kchannel) {
        final int w = bufferedImage.getWidth();
        final int h = bufferedImage.getHeight();
        // final int numChannels = cm.getNumComponents();
        //
        // if((kchannel && numChannels != 4) || (!kchannel && numChannels != 3)) {
        // // this makes no sense. CMYK should be 4 channel unless I miscounted the letters
        // // in CMYK
        // LOGGER.warn("Image has a CMYK color space but has " + numChannels + " channels");
        // return fallback(bufferedImage, cm);
        // }

        final int[] bitsPerChannel = ccmCeckBitsPerChannel(cm);
        try (final CvMat cmykMat = putDataBufferIntoMat(bufferedImage.getData().getDataBuffer(), h, w, bitsPerChannel.length);
                final Closer closer = new Closer();) {

            // do the inversion first
            Core.bitwise_not(cmykMat, cmykMat);

            if(kchannel) {
                try (final Closer c2 = new Closer();
                        final CvMat cmykInt = new CvMat()) {

                    // R = 255 x (1-C) x (1-K)
                    // R = (255 - C') x (1-K)
                    // R = (255 - C') x (255 - K')/255
                    //
                    // Our image is C'Y'M'K'
                    //
                    // so:
                    //
                    // 255 R = ~C' x ~K'
                    //
                    // R = (~C' x ~K') / 255

                    final CvMat cmyk32 = chain(new CvMat(), c -> c2.add(c), c -> cmykMat.convertTo(c, CvType.CV_32S));
                    final List<Mat> channels = new ArrayList<>(4);
                    Core.split(cmyk32, channels);

                    channels.forEach(m -> c2.addMat(m));
                    final List<Mat> bgrL = new ArrayList<>(3);
                    bgrL.add(channels.get(2));
                    bgrL.add(channels.get(1));
                    bgrL.add(channels.get(0));

                    final Mat k = channels.get(3);

                    final List<Mat> bgrXk = bgrL.stream()
                            .map(c -> c2.addMat(c.mul(k)))
                            // .map(c -> {
                            // System.out.println("CxK:");
                            // dump(c, 5, 5);
                            // return c;
                            // })
                            // TODO, change 255 to the right number depending on the size.
                            .map(c -> chain(c, m -> Core.multiply(m, new Scalar(1.0D / 255.0), m)))
                            // .map(c -> {
                            // System.out.println("CxK/255:");
                            // dump(c, 5, 5);
                            // return c;
                            // })
                            // TODO, change CvType.CV_8U to the right number depending on the size.
                            .map(c -> chain(c, m -> m.convertTo(c, CvType.CV_8U)))
                            // .map(c -> {
                            // System.out.println("CxK/255 -> 8U:");
                            // dump(c, 5, 5);
                            // return c;
                            // })
                            .collect(Collectors.toList());

                    final CvMat bgr = chain(new CvMat(), m -> Core.merge(bgrXk, m), m -> c2.add(m));
                    return bgr.returnMe();
                }
            } else {
                Imgproc.cvtColor(cmykMat, cmykMat, Imgproc.COLOR_RGB2BGR);
                return cmykMat;
            }
        }
    }

    private static boolean doFallback(final ComponentColorModel cm) {
        final ColorSpace colorSpace = cm.getColorSpace();
        return !(CvMatWithColorInformation.isLinearRGBspace(colorSpace)
                || colorSpace.getType() == ColorSpace.TYPE_GRAY
                || colorSpace.isCS_sRGB());
    }

    private static CvMat fallback(final BufferedImage bufferedImage, final ComponentColorModel cm) {
        final int w = bufferedImage.getWidth();
        final int h = bufferedImage.getHeight();
        try (final CvMat floatMat = new CvMat();
                final CvMat rawMat = putDataBufferIntoMat(bufferedImage.getRaster().getDataBuffer(), h, w, cm.getNumComponents());) {
            final int bpc = bitsPerChannel(rawMat.depth());

            double maxValue = 255.0D;
            if(bpc == 16)
                maxValue = 65535.0;
            else if(bpc != 8)
                throw new IllegalStateException("Can only handle 8 or 16 bit channels.");

            // normalize, convert to float first
            rawMat.convertTo(floatMat, CvType.CV_32F, 1.0D / maxValue);

            // floatMat is now normalized.
            final ColorSpace colorSpace = cm.getColorSpace();

            try (final CvMat mappedFloatMat = new CvMat(h, w, CvType.CV_32FC3);) {
                floatMat.rasterAp(flRas -> {
                    mappedFloatMat.rasterAp(mappedFloatRas -> {
                        flRas.forEach((FlatFloatPixelConsumer)(pos, pixel) -> {
                            final float[] result = colorSpace.toRGB(pixel);
                            // need bgr.
                            final float resZero = result[0];
                            result[0] = result[2];
                            result[2] = resZero;

                            mappedFloatRas.set(pos, result);
                        });
                    });
                });

                // okay. Now we need to scale out and convert
                try (CvMat ret = new CvMat()) {
                    mappedFloatMat.convertTo(ret, CvType.makeType(rawMat.depth(), 3), maxValue);
                    return ret.returnMe();
                }
            }
        }
    }

    private static CvMat handleCustomComponentColorModel(final BufferedImage bufferedImage, final ComponentColorModel cm) {
        // If the bufferedImage isn't sRGB, or Gray, or LinearRGB then
        // and it's an ICC color space, we should fallback to normalizing
        // the image and then using ColorSpace.getRGB(float[] pixel) for
        // each normalized pixel.
        if(doFallback(cm))
            return fallback(bufferedImage, cm);

        // if the ColorSpace includes profiles we simply use the fallback
        // final ColorSpace colorSpace = cm.getColorSpace();
        // if(ICC_ColorSpace.class.isAssignableFrom(colorSpace.getClass())) {
        // return fallback(bufferedImage, cm);
        // } else
        // System.out.println("NOT USING FALLBACK!!!!");

        // Check the ColorSpace type. If it's TYPE_CMYK then we can handle it
        // without the fallback.
        if(ColorSpace.TYPE_CMYK == cm.getColorSpace().getType())
            return handleCMYKColorSpace(bufferedImage, cm, true);
        else if(ColorSpace.TYPE_CMY == cm.getColorSpace().getType())
            return handleCMYKColorSpace(bufferedImage, cm, false);

        // // Check the colorspace. If it's not CS_sRGB then we need
        // // to fall back to the slow means of getting the data an
        // // let the ImageIO plugin convert to 8-bits/channel (A)RGB
        // if(!cm.getColorSpace().isCS_sRGB())
        // return fallback(bufferedImage, cm);

        final int w = bufferedImage.getWidth();
        final int h = bufferedImage.getHeight();

        final int[] bitsPerChannel = ccmCeckBitsPerChannel(cm);
        final int bpc = bitsPerChannel[0];

        // Now, do we have an 8-bit RGB or a 16-bit (per-channel) RGB
        if(bpc > 8 && bpc <= 16) {
            final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
            // make sure the DataBuffer type is a DataBufferUShort
            if(!(dataBuffer instanceof DataBufferUShort))
                throw new IllegalArgumentException("For a 16-bit per channel RGB image the DataBuffer type should be a DataBufferUShort but it's a "
                        + dataBuffer.getClass().getSimpleName());
            try (CvMat ret = dataBufferUShortToMat((DataBufferUShort)dataBuffer, h, w, cm.getNumComponents());) {
                if(bpc != 16) {
                    final double maxPixelValue = 65536.0D;
                    final double scale = ((maxPixelValue - 1) / ((1 << bpc) - 1));
                    Core.multiply(ret, new Scalar(scale), ret);
                }
                return ret.returnMe();
            }
        } else if(bpc == 8) {
            final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
            // make sure the DataBuffer type is a DataBufferByte
            if(!(dataBuffer instanceof DataBufferByte))
                throw new IllegalArgumentException("For a 8-bit per channel RGB image the DataBuffer type should be a DataBufferByte but it's a "
                        + dataBuffer.getClass().getSimpleName());
            return dataBufferByteToMat((DataBufferByte)dataBuffer, h, w, cm.getNumComponents(), cm.getColorSpace().getType() == ColorSpace.TYPE_RGB);
        } else
            throw new IllegalArgumentException(
                    "Cannot handle an image with a ComponentColorModel that has " + bpc + " bits per channel.");
    }

    private static CvMat handleDirectColorModel(final BufferedImage bufferedImage, final DirectColorModel cm) {
        final int w = bufferedImage.getWidth();
        final int h = bufferedImage.getHeight();
        final boolean hasAlpha = cm.hasAlpha();
        try (final CvMat rawMat = putDataBufferIntoMat(bufferedImage.getRaster().getDataBuffer(), h, w, 1);
                CvMat ret = transformDirect(rawMat, hasAlpha, cm.isAlphaPremultiplied(), cm.getMasks());) {
            return ret.returnMe();
        }
    }

    /**
     * This will right shift and mask. Let's take an example. Suppose you have an CV_32S (32-bit signed int) Mat
     * with a
     * ARGB 8-bit per channel layout. You can extract the 'R' channel using the following call:
     * {@code bitwiseUnsignedRightShiftAndMask( src, dst, 16, 8 );}.
     *
     * A more complicated example might be: you have an 16-bit 565/RGB layout. Here the most-significant 5 bits
     * are Red, The next 6 bits are the unsigned Green value, the least-significant 5 bits are the Blue value. You
     * can
     * extract the Red using the following call: {@code bitwiseUnsignedRightShiftAndMask( src, dst, 11, 5 );}.
     * Extracting each channel would be:
     *
     * <pre>
     * <code>
     *    bitwiseUnsignedRightShiftAndMask(src, red, 11, 5);
     *    bitwiseUnsignedRightShiftAndMask(src, green, 5, 6);
     *    bitwiseUnsignedRightShiftAndMask(src, blue, 0, 5);
     * </code>
     * </pre>
     *
     * The destination {@link CvMat} will be the same {@code type} as the source so, continuing with the above
     * example,
     * if you want the final image to be CV_8UC3 you need to {@code convert} and {@code merge} the separate
     * channels.
     * For example:
     *
     * <pre>
     * <code>
     *    red.convertTo(red, CvType.CV8U);
     *    green.convertTo(green, CvType.CV8U);
     *    blue.convertTo(blue, CvType.CV8U);
     *    Core.merge(Arrays.asList(blue, green, red), finalMat);
     * </code>
     * </pre>
     *
     * @param toShift is the source Mat to shift
     * @param dst is the destination Mat
     * @param shift is the number of bits to shift
     * @param numBitsInField is the number of bits in the entire number being shifted.
     */
    public static void bitwiseUnsignedRightShiftAndMask(final Mat toShift, final Mat dst, final int shift, final int numBitsInField) {
        final int divisor = 1 << shift; // e.g. if shift is 8, divisor is 256
        final int maskLsb = divisor - 1; // e.g. if shift is 8, mask = 255 = 0x000000ff
        final int type = toShift.type();
        final int bitsInSrcField = bitsPerChannel(type);

        if(numBitsInField + shift > bitsInSrcField)
            throw new IllegalArgumentException(
                    "The number of bits in the field being shifted (" + numBitsInField + ") along with the amount to shift (" + shift
                            + ") is greater than the size of the field itself (" + bitsInSrcField + ")");

        final int msbMask = (1 << numBitsInField) - 1;

        try (final CvMat maskMat = new CvMat();
                final CvMat msbMaskMat = new CvMat();) {

            // Filter chop lower bits
            if(shift > 0) {
                // maskLsb is a mask that when ANDED with, KEEPS the LS bits. We need
                // to CUT the LSB prior to shifting, so we negate maskLsb.
                makeScalarMat(~maskLsb, type, maskMat);
                // System.out.println("mask scalar mat:");
                // dump(maskMat);
                Core.bitwise_and(toShift, maskMat, dst); // mask out LSBs that are where we're going to shift into
                // System.out.println("Mat & mask");
                // dump(dst, 13, 13);
                Core.multiply(dst, new Scalar(1.0D / divisor), dst); // shift all of values in the channel >> shift.
            } else {
                try (CvMat tmp = CvMat.deepCopy(toShift)) {
                    CvMat.reassign(dst, tmp);
                }
            }
            makeScalarMat(msbMask, type, msbMaskMat);
            // System.out.println("msbMask mat");
            // dump(msbMaskMat);
            Core.bitwise_and(dst, msbMaskMat, dst);
        }
    }

    // All DirectColorModel values are stored RGBA. We want them reorganized a BGRA
    private static int[] bgraOrderDcm = {2,1,0,3};

    // The DirectColorModel mask array is returned as R,G,B,A. This method expects
    // it in that order.
    private static CvMat transformDirect(final CvMat rawMat, final boolean hasAlpha, final boolean isAlphaPremultiplied, final int[] rgbaMasks) {
        if(LOGGER.isTraceEnabled())
            LOGGER.trace("transformDirect: {} and has alpha", rawMat, hasAlpha);
        final int numChannels = rgbaMasks.length;

        // According to the docs on DirectColorModel the type MUST be TYPE_RGB which
        // means 3 channels or 4 if there's an alpha.
        final int expectedNumberOfChannels = hasAlpha ? 4 : 3;
        if(expectedNumberOfChannels != numChannels)
            throw new IllegalArgumentException("The DirectColorModel doesn't seem to contain either 3 or 4 channels. It has " + numChannels);

        // Fetch the masks and bitsPerChannel in the OCV BGRA order.
        final int[] bgraMasks = new int[hasAlpha ? 4 : 3];
        final int[] bitsPerChannel = new int[hasAlpha ? 4 : 3];
        for(int rgbch = 0; rgbch < bgraMasks.length; rgbch++) {
            final int mask = rgbaMasks[rgbch];
            bgraMasks[bgraOrderDcm[rgbch]] = mask;
            bitsPerChannel[bgraOrderDcm[rgbch]] = Integer.bitCount(mask);
        }
        final int[] shifts = determineShifts(bgraMasks);

        // check if any channel has a bits-per-channel > 16
        if(Arrays.stream(bitsPerChannel)
                .filter(v -> v > 16)
                .findAny()
                .isPresent())
            throw new IllegalArgumentException("The image with the DirectColorModel has a channel with more than 16 bits " + bitsPerChannel);

        double maxValue = 255.0D;
        int componentDepth = CV_8U;
        for(int i = 0; i < bitsPerChannel.length; i++) {
            final int n = bitsPerChannel[i];
            if(n > 8) {
                componentDepth = CV_16U;
                maxValue = 65535.0D;
                break;
            }
        }

        // System.out.println("Raw Mat");
        // dump(rawMat, 5, 5);

        try (final CvMat remergedMat = new CvMat();
                Closer closer = new Closer()) {

            // we're going to separate the channels into separate Mat's by masking
            final List<Mat> channels = new ArrayList<>(numChannels);

            for(int ch = 0; ch < numChannels; ch++) {
                try (CvMat tmpCurChannel = new CvMat();) {
                    bitwiseUnsignedRightShiftAndMask(rawMat, tmpCurChannel, shifts[ch], bitsPerChannel[ch]);
                    // if the bits don't take up the entire channel then we need to scale them.
                    // for example, if we have a 4/4/4 image we need to scale the results to 8 bits.

                    final CvMat curChannel = closer.add(new CvMat());
                    tmpCurChannel.convertTo(curChannel, componentDepth);

                    // System.out.println("Channel " + ch + " pre scaled:");
                    // dump(curChannel, 5, 5);

                    // This will scale the maximum value given the field to the maximum value
                    // of the final field.
                    final double scale = maxValue / ((1 << bitsPerChannel[ch]) - 1);
                    Core.multiply(curChannel, new Scalar(scale), curChannel);

                    // System.out.println("Channel " + ch + " scaled by " + scale);
                    // dump(curChannel, 5, 5);

                    channels.add(curChannel);
                }
            }

            if(isAlphaPremultiplied) {
                final Mat alpha = channels.get(3);
                for(int ch = 0; ch < 3; ch++) {
                    final Mat cur = channels.get(ch);
                    Core.divide(cur, alpha, cur);
                    cur.convertTo(cur, componentDepth);
                }
                alpha.convertTo(alpha, componentDepth);
            }

            // now merge the channels
            Core.merge(channels, remergedMat);

            // System.out.println("Remerged Mat: ");
            // dump(remergedMat, 5, 5);

            return remergedMat.returnMe();
        }
    }

    public static CvMatWithColorInformation img2CvMat(final BufferedImage bufferedImage) {
        final ColorModel colorModel = bufferedImage.getColorModel();

        try (Closer closer = new Closer()) {
            if(colorModel instanceof DirectColorModel) {
                return new CvMatWithColorInformation(closer.add(handleDirectColorModel(bufferedImage, (DirectColorModel)colorModel)), bufferedImage);
            } else if(colorModel instanceof ComponentColorModel) {
                return new CvMatWithColorInformation(handleComponentColorModel(bufferedImage, (ComponentColorModel)colorModel), bufferedImage);
            } else
                throw new IllegalArgumentException("Can't handle a BufferedImage with a " + colorModel.getClass().getSimpleName() + " color model.");
        }
    }

    public static void print(final String prefix, final Mat im) {
        System.out
                .println(prefix + " { depth=(" + CvType.ELEM_SIZE(im.type()) + "(" + CvType.typeToString(im.type()) + "), " + im.depth() + "), channels="
                        + im.channels() + " HxW=" + im.height() + "x" + im.width() + " }");
    }

    /**
     * Find the point on the line defined by {@code perpRef} that's closest to the
     * point {@code x}. Note, {@link PerpendicularLine} is poorly named.
     */
    public static Point closest(final Point x, final PerpendicularLine perpRef) {
        return closest(x, perpRef.x(), perpRef.y());
    }

    public static void drawCircle(final Point p, final Mat ti, final Color color) {
        drawCircle(p, ti, color, 10);
    }

    public static void drawCircle(final int row, final int col, final Mat ti, final Color color) {
        drawCircle(row, col, ti, color, 10);
    }

    public static void drawCircle(final Point p, final Mat ti, final Color color, final int radius) {
        Imgproc.circle(ti, new org.opencv.core.Point(((int)(p.getCol() + 0.5)) - radius, ((int)(p.getRow() + 0.5)) - radius),
                radius, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static void drawCircle(final int row, final int col, final Mat ti, final Color color, final int radius) {
        Imgproc.circle(ti, new org.opencv.core.Point(((int)(col + 0.5)) - radius, ((int)(row + 0.5)) - radius),
                radius, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static void drawCircle(final int row, final int col, final Graphics2D g, final Color color, final int radius) {
        g.setColor(color);

        g.drawOval(((int)(col + 0.5)) - radius,
                ((int)(row + 0.5)) - radius,
                2 * radius, 2 * radius);
    }

    public static void drawCircle(final int row, final int col, final Graphics2D g, final Color color) {
        drawCircle(row, col, g, color, 10);
    }

    public static void drawCircle(final Point p, final Graphics2D g, final Color color) {
        drawCircle((int)p.getRow(), (int)p.getCol(), g, color, 10);
    }

    public static void drawBoundedPolarLine(final Point bound1, final Point bound2, final double r, final double c, final Mat ti, final Color color) {
        drawLine(closest(bound1, c, r), closest(bound2, c, r), ti, color);
    }

    public static void drawLine(final Point p1, final Point p2, final Mat ti, final Color color) {
        Imgproc.line(ti, new org.opencv.core.Point(p1.getCol(), p1.getRow()),
                new org.opencv.core.Point(p2.getCol(), p2.getRow()),
                new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static void drawLine(final Point p1, final Point p2, final Graphics2D g, final Color color) {
        g.setColor(color);
        g.drawLine((int)(p1.getCol() + 0.5), (int)(p1.getRow() + 0.5), (int)(p2.getCol() + 0.5), (int)(p2.getRow() + 0.5));
    }

    public static void drawPolarLine(final double r, final double c, final Mat ti, final Color color) {
        drawPolarLine(r, c, ti, color, 0, 0, ti.rows() - 1, ti.cols() - 1);
    }

    public static void drawPolarLine(final double r, final double c, final Mat ti, final Color color,
            final int boundingr1, final int boundingc1, final int boundingr2, final int boundingc2) {
        drawPolarLine(r, c, ti, color, boundingr1, boundingc1, boundingr2, boundingc2, 0, 0);
    }

    public static void drawPolarLine(final double r, final double c, final Mat ti, final Color color,
            int boundingr1, int boundingc1, int boundingr2, int boundingc2,
            final int translater, final int translatec) {
        int tmpd;
        if(boundingr1 > boundingr2) {
            tmpd = boundingr1;
            boundingr1 = boundingr2;
            boundingr2 = tmpd;
        }

        if(boundingc1 > boundingc2) {
            tmpd = boundingc1;
            boundingc1 = boundingc2;
            boundingc2 = tmpd;
        }

        // a polar line represented by r,c is a perpendicular to
        // the line from the origin to the point r,c. The line
        // from the origin to this point in rad,theta is given
        // by:
        //
        // rad = sqrt(r^2 + c^2)
        // theta = tan^-1(r/c)
        // (where theta is measured from the top of the
        // image DOWN to the point r,c)
        //
        // anyway - the line is represented by:
        // x cos(theta) + y sin (theta) = r

        final double rad = Math.sqrt((r * r) + (c * c));

        // we need to find the endpoints of the line:
        int r1, c1, r2, c2;

        // lets remove the simple possiblities
        if(c == 0.0) {
            r1 = r2 = (int)(rad + 0.5);
            c1 = boundingc1;
            c2 = boundingc2;
        } else if(r == 0.0) {
            c1 = c2 = (int)(rad + 0.5);
            r1 = boundingr1;
            r2 = boundingr2;
        } else {
            final double sintheta = r / rad;
            final double costheta = c / rad;

            // x cos th + y sin th = r =>
            // x (xc/r) + y (yc/r) = r (by definition of sin and cos) =>
            // x xc + y yc = r^2 =>
            // X.Xc = r^2 - (no duh!)

            // find the points at the boundaries

            // where does the line intersect the left/right boundary
            // bc costh + ir sinth = r =>
            //
            // r - bc costh
            // ir = -------------
            // sinth
            //
            final double leftIntersetingRow = (rad - ((boundingc1) * costheta)) / sintheta;
            final double rightIntersetingRow = (rad - ((boundingc2) * costheta)) / sintheta;

            // where does the line intersect the top/bottom boundary
            // ic costh + br sinth = r =>
            //
            // r - br sinth
            // ic = -------------
            // costh
            //
            final double topIntersectingCol = (rad - ((boundingr1) * sintheta)) / costheta;
            final double botIntersectingCol = (rad - ((boundingr2) * sintheta)) / costheta;

            // now, which pair works the best
            c1 = r1 = -1;
            if(leftIntersetingRow >= boundingr1 && leftIntersetingRow <= boundingr2) {
                c1 = boundingc1;
                r1 = (int)(leftIntersetingRow + 0.5);
            } else if(topIntersectingCol >= boundingc1 && topIntersectingCol <= boundingc2) {
                c1 = boundingr1;
                r1 = (int)(topIntersectingCol + 0.5);
            } else if(rightIntersetingRow >= boundingr1 && rightIntersetingRow <= boundingr2) {
                c1 = boundingc2;
                r1 = (int)(rightIntersetingRow + 0.5);
            } else if(botIntersectingCol >= boundingc1 && botIntersectingCol <= boundingc2) {
                c1 = boundingr2;
                r1 = (int)(botIntersectingCol + 0.5);
            }

            if(c1 == -1 && r1 == -1) // no part of the line intersects the box
                // {
                // System.out.println( " line " + r + "," + c + " does not intesect " +
                // boundingr1 + "," + boundingc1 + "," + boundingr2 + "," + boundingc2);
                return;
            // }

            // now search in the reverse direction for the other point
            c2 = r2 = -1;
            if(botIntersectingCol >= boundingc1 && botIntersectingCol <= boundingc2) {
                c2 = boundingr2;
                r2 = (int)(botIntersectingCol + 0.5);
            } else if(rightIntersetingRow >= boundingr1 && rightIntersetingRow <= boundingr2) {
                c2 = boundingc2;
                r2 = (int)(rightIntersetingRow + 0.5);
            } else if(topIntersectingCol >= boundingc1 && topIntersectingCol <= boundingc2) {
                c2 = boundingr1;
                r2 = (int)(topIntersectingCol + 0.5);
            } else if(leftIntersetingRow >= boundingr1 && leftIntersetingRow <= boundingr2) {
                c2 = boundingc1;
                r2 = (int)(leftIntersetingRow + 0.5);
            }

            // now, the two points should not be the same ... but anyway
        }

        Imgproc.line(ti, new org.opencv.core.Point(c1 + translatec, r1 + translater),
                new org.opencv.core.Point(c2 + translatec, r2 + translater),
                new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    /**
     * This method will overlay the {@code overlay} image onto the {@code original}
     * image by having the original image show through the overlay, everywhere the
     * overlay has a pixel value of zero (or zero in all channels).
     */
    public static void overlay(final CvMat original, final CvMat dst, final CvMat overlay) {
        try (final CvMat gray = new CvMat();
                final CvMat invMask = new CvMat();
                CvMat maskedOrig = new CvMat()) {
            Imgproc.cvtColor(overlay, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, invMask, 1, 255, Imgproc.THRESH_BINARY_INV);
            Core.bitwise_and(original, original, maskedOrig, invMask);
            Core.add(original, overlay, dst);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T toArray(final Mat mat, final Class<T> clazz) {
        final int rows = mat.rows();
        if(rows == 0) {
            Class<?> component = clazz;
            while(component.isArray())
                component = component.getComponentType();
            return (T)Array.newInstance(component, 0, 0, 0);
        }

        final int type = mat.type();
        final int channels = CvType.channels(type);

        final int cols = mat.cols();
        return CvMat.rasterOp(mat, raster -> {
            final T ret;
            switch(CvType.depth(type)) {
                case CvType.CV_8S:
                case CvType.CV_8U:
                    ret = (T)Array.newInstance(byte.class, rows, cols, channels);
                    raster.forEach((BytePixelConsumer)(r, c, p) -> System.arraycopy(p, 0, ((byte[][][])ret)[r][c], 0, channels));
                    break;
                case CvType.CV_16S:
                case CvType.CV_16U:
                    ret = (T)Array.newInstance(short.class, rows, cols, channels);
                    raster.forEach((ShortPixelConsumer)(r, c, p) -> System.arraycopy(p, 0, ((short[][][])ret)[r][c], 0, channels));
                    break;
                case CvType.CV_32S:
                    ret = (T)Array.newInstance(int.class, rows, cols, channels);
                    raster.forEach((IntPixelConsumer)(r, c, p) -> System.arraycopy(p, 0, ((int[][][])ret)[r][c], 0, channels));
                    break;
                case CvType.CV_32F:
                    ret = (T)Array.newInstance(float.class, rows, cols, channels);
                    raster.forEach((FloatPixelConsumer)(r, c, p) -> System.arraycopy(p, 0, ((float[][][])ret)[r][c], 0, channels));
                    break;
                case CvType.CV_64F:
                    ret = (T)Array.newInstance(double.class, rows, cols, channels);
                    raster.forEach((FloatPixelConsumer)(r, c, p) -> System.arraycopy(p, 0, ((double[][][])ret)[r][c], 0, channels));
                    break;
                default:
                    throw new IllegalArgumentException("Can't handle CvType with value " + CvType.typeToString(type));
            }
            return ret;
        });
    }

    public static double[] toDoubleArray(final Mat mat) {
        if(mat.type() != CvType.CV_64FC1)
            throw new IllegalArgumentException("Cannot convert mat " + mat + " to a double[] given the type " + CvType.typeToString(mat.type())
                    + " since it must be " + CvType.typeToString(CvType.CV_64FC1));
        // we're going to flatten it.
        final int r = mat.rows();
        final int c = mat.cols();
        final int len = r * c;
        final double[] ret = new double[len];

        try (final CvMat toCvrt = CvMat.move(mat.reshape(0, len));) {
            for(int i = 0; i < len; i++)
                ret[i] = toCvrt.get(i, 0)[0];
        }
        return ret;
    }

    public static double[][] to2dDoubleArray(final Mat mat) {
        if(mat.type() != CvType.CV_64FC1)
            throw new IllegalArgumentException("Cannot convert mat " + mat + " to a double[] given the type " + CvType.typeToString(mat.type())
                    + " since it must be " + CvType.typeToString(CvType.CV_64FC1));
        // we're going to flatten it.
        final int r = mat.rows();
        final int c = mat.cols();
        final double[][] ret = new double[r][c];

        for(int i = 0; i < r; i++)
            for(int j = 0; j < c; j++)
                ret[i][j] = mat.get(i, j)[0];

        return ret;
    }

    public static CvMat toMat(final double[] a, final boolean row) {
        final int len = a.length;
        try (final CvMat ret = new CvMat(row ? 1 : len, row ? len : 1, CvType.CV_64FC1);) {
            ret.rasterAp(raster -> raster.apply((FlatDoublePixelSetter)i -> new double[] {a[i]}));
            return ret.returnMe();
        }
    }

    public static CvMat toMat(final double[][] a) {
        final int rows = a.length;
        final int cols = a[0].length;

        try (final CvMat ret = new CvMat(rows, cols, CvType.CV_64FC1);) {
            ret.rasterAp(raster -> raster.apply((DoublePixelSetter)(r, c) -> new double[] {a[r][c]}));
            return ret.returnMe();
        }
    }

    public static CvMat toMat(final float[][] a) {
        final int rows = a.length;
        final int cols = a[0].length;

        try (final CvMat ret = new CvMat(rows, cols, CvType.CV_32FC1);) {
            ret.rasterAp(raster -> raster.apply((FloatPixelSetter)(r, c) -> new float[] {a[r][c]}));
            return ret.returnMe();
        }
    }

    public static CvMat pointsToColumns2D(final Mat undistoredPoint) {
        final MatOfPoint2f matOfPoints = new MatOfPoint2f(undistoredPoint);
        try (final QuietCloseable destroyer = () -> CvMat.move(matOfPoints).close();) {
            final double[][] points = matOfPoints.toList().stream()
                    .map(p -> new double[] {p.x,p.y})
                    .toArray(double[][]::new);
            try (CvMat pointsAsMat = Utils.toMat(points);) {
                return CvMat.move(pointsAsMat.t());
            }
        }
    }

    public static CvMat pointsToColumns3D(final Mat undistoredPoint) {
        final MatOfPoint3f matOfPoints = new MatOfPoint3f(undistoredPoint);
        try (final QuietCloseable destroyer = () -> CvMat.move(matOfPoints).close();) {
            final double[][] points = matOfPoints.toList().stream()
                    .map(p -> new double[] {p.x,p.y,p.z})
                    .toArray(double[][]::new);
            try (CvMat pointsAsMat = Utils.toMat(points);) {
                return CvMat.move(pointsAsMat.t());
            }
        }
    }

    public static Size preserveAspectRatio(final Mat mat, final Size newSize) {
        return preserveAspectRatio(mat.size(), newSize);
    }

    public static Size preserveAspectRatio(final Size originalMatSize, final Size newSize) {
        // calculate the appropriate resize
        final double fh = newSize.height / originalMatSize.height;
        final double fw = newSize.width / originalMatSize.width;
        final double scale = fw < fh ? fw : fh;
        return (scale >= 1.0) ? new Size(originalMatSize.width, originalMatSize.height)
                : new Size(Math.round(originalMatSize.width * scale), Math.round(originalMatSize.height * scale));
    }

    private static Point closest(final Point x, final double perpRefX, final double perpRefY) {
        // Here we use the description for the perpendicularDistance.
        // if we translate X0 to the origin then Xi' (defined as
        // Xi translated by X0) will be at |P| - (P.X0)/|P| (which
        // is the signed magnitude of the X0 - Xi where the sign will
        // be positive if X0 X polar(P) is positive and negative
        // otherwise (that is, if X0 is on the "lower" side of the polar
        // line described by P)) along P itself. So:
        //
        // Xi' = (|P| - (P.X0)/|P|) Pu = (|P| - (P.X0)/|P|) P/|P|
        // = (1 - (P.X0)/|P|^2) P (where Pu is the unit vector in the P direction)
        //
        // then we can translate it back by X0 so that gives:
        //
        // Xi = (1 - (P.X0)/|P|^2) P + X0 = c P + X0
        // where c = (1 - (P.X0)/|P|^2)
        final double Pmagsq = (perpRefX * perpRefX) + (perpRefY * perpRefY);
        final double PdotX0 = (x.y() * perpRefY) + (x.x() * perpRefX);

        final double c = (1.0 - (PdotX0 / Pmagsq));
        return new SimplePoint((c * perpRefY) + x.y(), (c * perpRefX) + x.x());
    }

    private static Field getStaticField(final String fieldName, final Class<?>... classes) {
        final Field field = Arrays.stream(classes)
                .map(c -> {
                    try {
                        return c.getDeclaredField(fieldName);
                    } catch(final NoSuchFieldException nsfe) {
                        return null;
                    }
                })
                .filter(f -> f != null)
                .findFirst()
                .orElse(null);

        if(field == null)
            throw new IllegalStateException("The version of OpenCV defined as a dependency doesn't seem to have " + fieldName
                    + "  defined in any of these classes: " + Arrays.toString(classes));
        return field;
    }

    private static Method getStaticMethod(final String methodName, final Class<?>[] parameters, final Class<?>... classes) {
        final Method method = Arrays.stream(classes)
                .map(c -> {
                    try {
                        return c.getDeclaredMethod(methodName, parameters);
                    } catch(final NoSuchMethodException nsfe) {
                        return null;
                    }
                })
                .filter(f -> f != null)
                .findFirst()
                .orElse(null);

        if(method == null)
            throw new IllegalStateException("The version of OpenCV defined as a dependency doesn't seem to have the method " + methodName
                    + "  defined in any of these classes: " + Arrays.toString(classes));
        return method;
    }

    private static CvMat abgrDataBufferByteToMat(final DataBufferByte bb, final int h, final int w, final boolean hasAlpha) {
        try (final CvMat retMat = new CvMat(h, w, hasAlpha ? CvType.CV_8UC4 : CvType.CV_8UC3);) {
            final byte[] inpixels = bb.getData();
            retMat.put(0, 0, inpixels);
            if(!hasAlpha) { // indicates a pixel compatible format since the only option is TYPE_3BYTE_BGR
                return retMat.returnMe();
            } else { // then it's ABGR -> BGRA
                try (final CvMat reshaped = CvMat.move(retMat.reshape(1, h * w));
                        // type to 32F so we can multiple it by the matrix. It would be nice of there
                        // was an integer 'gemm'
                        // call on Imgproc
                        final CvMat typed = Functional.chain(new CvMat(), m -> reshaped.convertTo(m, CvType.CV_32F));
                        // color transform which just reorganizes the pixels.
                        final CvMat xformed = typed.mm(abgr2bgra);
                        // reshape it back to a 4 channel image
                        final CvMat xformedAndShaped = CvMat.move(xformed.reshape(4, h));
                        // convert the type back to CV_8UC4
                        final CvMat it = Functional.chain(new CvMat(), m -> xformedAndShaped.convertTo(m, CvType.depth(retMat.type())));) {
                    return it.returnMe();
                }
            }
        }
    }

    private static CvMat putDataBufferIntoMat(final DataBuffer bb, final int h, final int w, final int numChannels) {
        if(bb instanceof DataBufferByte)
            return putDataBufferByteIntoMat((DataBufferByte)bb, h, w, numChannels);
        else if(bb instanceof DataBufferUShort)
            return putDataBufferUShortIntoMat((DataBufferUShort)bb, h, w, numChannels);
        else if(bb instanceof DataBufferInt)
            return putDataBufferIntIntoMat((DataBufferInt)bb, h, w, numChannels);
        else
            throw new IllegalArgumentException("Can't handle a DataBuffer of type " + bb.getClass().getSimpleName());
    }

    private static CvMat putDataBufferIntIntoMat(final DataBufferInt bb, final int h, final int w, final int numChannels) {
        try (final CvMat ret = new CvMat(h, w, CvType.makeType(CV_32S, numChannels));) {
            final int[] inpixels = bb.getData();
            ret.put(0, 0, inpixels);
            return ret.returnMe();
        }
    }

    private static CvMat putDataBufferUShortIntoMat(final DataBufferUShort bb, final int h, final int w, final int numChannels) {
        try (final CvMat ret = new CvMat(h, w, CvType.makeType(CV_16U, numChannels));) {
            final short[] inpixels = bb.getData();
            ret.put(0, 0, inpixels);
            return ret.returnMe();
        }
    }

    private static CvMat putDataBufferByteIntoMat(final DataBufferByte bb, final int h, final int w, final int numChannels) {
        try (final CvMat ret = new CvMat(h, w, CvType.makeType(CV_8U, numChannels));) {
            final byte[] inpixels = bb.getData();
            ret.put(0, 0, inpixels);
            return ret.returnMe();
        }
    }

    private static CvMat dataBufferUShortToMat(final DataBufferUShort bb, final int h, final int w, final int numChannels) {
        try (final CvMat mat = new CvMat(h, w, CvType.makeType(CvType.CV_16U, numChannels));) {
            final short[] inpixels = bb.getData();
            mat.put(0, 0, inpixels);
            return mat.returnMe();
        }
    }

    private final static int[] rgb2bgr = {2,1,0};
    private final static int[] argb2bgra = {3,2,1,0};

    private static CvMat dataBufferByteToMat(final DataBufferByte bb, final int h, final int w, final int numChannels, final boolean rgbType) {
        final byte[][] bankdata = bb.getBankData();
        if(bankdata.length == 1) {
            try (final CvMat mat = new CvMat(h, w, CvType.makeType(CvType.CV_8U, numChannels));) {
                final byte[] inpixels = bankdata[0];
                mat.put(0, 0, inpixels);
                return mat.returnMe();
            }
        } else {
            // bank data must corespond to the channels.
            final int[] lookup = rgbType ? (numChannels == 4 ? argb2bgra : (numChannels == 3 ? rgb2bgr : null)) : null;
            if(numChannels != bankdata.length)
                throw new IllegalStateException("Can't handle a BufferedImage where the data is in banks but it's not 1 per channel. The number of channels is "
                        + numChannels + " while the number of banks is " + bankdata.length);

            try (Closer closer = new Closer();
                    CvMat ret = new CvMat();) {
                final List<Mat> channels = new ArrayList<>(numChannels);
                for(int ch = 0; ch < numChannels; ch++) {
                    final CvMat cur = closer.add(new CvMat(h, w, CvType.CV_8UC1));
                    if(lookup == null)
                        cur.put(0, 0, bankdata[ch]);
                    else
                        cur.put(0, 0, bankdata[lookup[ch]]);
                    channels.add(cur);
                }
                Core.merge(channels, ret);
                return ret.returnMe();
            }
        }
    }
}
