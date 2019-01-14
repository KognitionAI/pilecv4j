package ai.kognition.pilecv4j.image;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_CUSTOM;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.Closer;
import ai.kognition.pilecv4j.image.CvRaster.DoublePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.FlatDoublePixelSetter;
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

   /**
    * This is set through reflection because the source changed between OpenCV 3 and OpenCV 4.
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

   // Dynamically determine if we're at major version 3 or 4 of OpenCV and set the variables appropriately.
   static {
      // this 3.x.x uses Core while 4.x.x uses Imgproc
      OCV_FONT_HERSHEY_SIMPLEX = uncheck(() -> Integer.valueOf(getStaticField("FONT_HERSHEY_SIMPLEX", Core.class, Imgproc.class).getInt(null))).intValue();
      OCV_UNDISTORT_METHOD = getStaticMethod("undistort", new Class<?>[] {Mat.class,Mat.class,Mat.class,Mat.class}, Imgproc.class, Calib3d.class);
      OCV_UNDISTORT_POINTS_METHOD = getStaticMethod("undistortPoints",
            new Class<?>[] {MatOfPoint2f.class,MatOfPoint2f.class,Mat.class,Mat.class,Mat.class,Mat.class},
            Imgproc.class, Calib3d.class);
   }

   /**
    * This method simply proxies a call to OpenCV's
    * <a href=
    * "https://docs.opencv.org/4.0.1/d9/d0c/group__calib3d.html#ga69f2545a8b62a6b0fc2ee060dc30559d">undistort</a>
    * method in order to provide compatibility between OpenCV 3 and OpenCV 4 when it was moved from
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
    * method in order to provide compatibility between OpenCV 3 and OpenCV 4 when it was moved from
    * {@code Imgproc} to {@code Calib3d}.
    */
   public static void undistortPoints(final MatOfPoint2f src, final MatOfPoint2f dst, final Mat cameraMatrix, final Mat distCoeffs, final Mat R, final Mat P) {
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
    * Convert an OpenCV <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> (or a
    * {@link CvMat}) to a {@link BufferedImage} that can be used in java swing and awt. Currently this can handle:
    * </p>
    * <ul>
    * <li>A single channel grayscale image of 8 or 16 bits.</li>
    * <li>A 3 channel color image of 8-bits per channel.*</li>
    * <li>A 4 channel color image with an alpha channel of 8-bits per channel.*</li>
    * </ul>
    *
    * <p>
    * <em>* Note: the method assumes color <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat's</a>
    * are in typical OpenCV BGR (or, for 4 channel images aBGR) format.</em>
    * </p>
    *
    * <p>
    * 8-bit per channel color images will be transformed to {@link BufferedImage}s of type
    * {@link BufferedImage#TYPE_3BYTE_BGR} for 3 channel images and {@link BufferedImage#TYPE_4BYTE_ABGR} for 4 channel
    * images.
    * </p>
    *
    * TODO: 16-bit per channel color images
    *
    * @param in <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> to be converted
    * @return new {@link BufferedImage} from the
    *         <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
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
               // type to 23F
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
    * Convert an OpenCV <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> (or a
    * {@link CvMat}) to a {@link BufferedImage} that can be used in java swing and awt using a specific index
    * color model (see {@link IndexColorModel}).
    * </p>
    *
    * <p>
    * This is a much simpler implementation that {@link Utils#mat2Img(Mat)} in that it only handles a
    * 1-channel, 8-bit image but allows you to assign colors to each of the 256 values. This is primarily
    * used to generated overlays on other images and represents a "poor-man's" alpha channel manipulation
    * in OpenCV which doesn't really have much in the way of alpha channel handling natively.
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
    * You can use this method to dump the contents of a
    * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> to the {@link PrintStream}.
    * Please note this is a really bad idea for large images but can help with debugging problems when
    * you're using OpenCV for it's linear-algebra/matrix capabilities.
    *
    * @param raster to dump print to the {@link PrintStream}
    * @param out is the {@link PrintStream} to dump the {@link CvRaster} to.
    * @param numRows limit the number of rows to the given number. Supply -1 for the all rows.
    * @param numCols limit the number of columns to the given number. Supply -1 for the all columns.
    */
   @SuppressWarnings("unchecked")
   public static void dump(final CvRaster raster, final PrintStream out, int numRows, int numCols) {
      if(numRows < 0) numRows = raster.rows();
      if(numCols < 0) numCols = raster.cols();

      out.println(raster.mat);
      @SuppressWarnings("rawtypes")
      final PixelConsumer pp = CvRaster.makePixelPrinter(out, raster.type());
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

   /**
    * This is a convenience method for {@link Utils#dump(Mat, PrintStream)} that uses {@link System#out} as the
    * {@link PrintStream}
    */
   public static void dump(final Mat mat, final int numRows, final int numCols) {
      dump(mat, System.out, numRows, numCols);
   }

   /**
    * You can use this method to dump the contents of a {@link CvRaster} to the {@link PrintStream}.
    * Please note this is a really bad idea for large images but can help with debugging problems when
    * you're using OpenCV for it's linear-algebra/matrix capabilities.
    *
    * @param raster to dump print to the {@link PrintStream}
    * @param out is the {@link PrintStream} to dump the {@link CvRaster} to.
    * @param numRows limit the number of rows to the given number. Supply -1 for the all rows.
    * @param numCols limit the number of columns to the given number. Supply -1 for the all columns.
    */
   public static void dump(final Mat mat, final PrintStream out, final int numRows, final int numCols) {
      CvMat.rasterAp(mat, raster -> dump(raster, out, numRows, numCols));
   }

   private static String is16BitPerChannelBitRGB(final BufferedImage bufferedImage) {
      final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
      final ColorModel cm = bufferedImage.getColorModel();
      final int colorSpaceType = cm.getColorSpace().getType();
      final int numChannels = cm.getNumComponents();
      if(numChannels != 3)
         return "Cannot handle an image of type " + TYPE_CUSTOM + " with " + numChannels + " channels.";
      if(!(cm instanceof ComponentColorModel))
         return "Cannot handle an image of type " + TYPE_CUSTOM + " with a color model of type " + cm.getClass().getSimpleName();
      final int expectedPixSize = numChannels == 3 ? 48 : 64;
      if(cm.getPixelSize() != expectedPixSize)
         return "Cannot handle an image of type " + TYPE_CUSTOM + " with " + numChannels + " unless the bits per pixel is " + expectedPixSize
               + " but in this case the image has " + cm.getPixelSize();
      if(!(dataBuffer instanceof DataBufferUShort))
         return "Cannot handle a " + numChannels + " channel image of type " + TYPE_CUSTOM + " with a data buffer of type "
               + dataBuffer.getClass().getSimpleName();
      if(colorSpaceType != ColorSpace.TYPE_RGB)
         return "Cannot handle an image of type " + TYPE_CUSTOM + " with a color space type that's not TYPE_RGB (" + ColorSpace.TYPE_RGB
               + "). This image's color space type is " + colorSpaceType;

      return null;
   }

   private static void checkCustomHandlingPrereq(final BufferedImage bufferedImage) {
      final ColorModel cm = bufferedImage.getColorModel();
      final int colorSpaceType = cm.getColorSpace().getType();
      final int numChannels = cm.getNumComponents();
      if(numChannels != 3)
         throw new IllegalArgumentException("Cannot handle an image of type " + TYPE_CUSTOM + " with " + numChannels + " channels.");
      if(!(cm instanceof ComponentColorModel) && !(cm instanceof DirectColorModel))
         throw new IllegalArgumentException("Cannot handle an image of type " + TYPE_CUSTOM + " with a color model of type " + cm.getClass().getSimpleName());
      if(colorSpaceType != ColorSpace.TYPE_RGB)
         throw new IllegalArgumentException(
               "Cannot handle an image of type " + TYPE_CUSTOM + " with a color space type that's not TYPE_RGB (" + ColorSpace.TYPE_RGB
                     + "). This image's color space type is " + colorSpaceType);
   }

   private static CvMat handle555and565(final BufferedImage bufferedImage, final boolean is565) {
      final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
      final int w = bufferedImage.getWidth();
      final int h = bufferedImage.getHeight();
      if(!(dataBuffer instanceof DataBufferUShort))
         throw new IllegalArgumentException("BufferedImage should have a " + DataBufferUShort.class.getSimpleName() + " but instead has a "
               + dataBuffer.getClass().getSimpleName());
      final DataBufferUShort bb = (DataBufferUShort)dataBuffer;
      final short[] srcdataShort = bb.getData();
      final byte[] srcdata = new byte[srcdataShort.length * 2];
      ByteBuffer.wrap(srcdata).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(srcdataShort);

      try (final CvMat ret = new CvMat(h, w, CvType.CV_8UC2);) {
         ret.put(0, 0, srcdata);
         try (final CvMat c1 = Functional.chain(new CvMat(),
               m -> Imgproc.cvtColor(ret, m, is565 ? Imgproc.COLOR_BGR5652BGR : Imgproc.COLOR_BGR5552BGR));) {
            return c1.returnMe();
         }
      }

   }

   private static int[] determineDivisors(final int[] masks) {
      final int[] ret = new int[masks.length];
      for(int i = 0; i < masks.length; i++) {
         int mask = masks[i];
         int divisor = 1;
         if(mask != 0) {
            while((mask & 1) == 0) {
               mask >>>= 1;
               divisor <<= 1;
            }
         }
         ret[i] = divisor;
      }
      return ret;
   }

   /**
    * set the mask to a scalar = 1 channel 1x1 Mat the same type as the rawMat
    */
   private static void scalarToMat(final int mask, final int type, final CvMat toSet) {
      final Object maskPixel = CvRaster.intsToPixelConverter(type).apply(new int[] {mask});
      toSet.rasterAp(r -> r.set(0, maskPixel));
   }

   /**
    * This method will allow you to convert a swing/awt/imageio {@link BufferedImage} to a
    * {@link CvMat}.
    *
    * @param bufferedImage
    * @return the {@link CvMat} representation of the provided image. <b>Note: The caller owns the CvMat returned</b>
    *         see {@link CvMat} for more details.
    */
   public static CvMat img2CvMat(final BufferedImage bufferedImage) {
      final int w = bufferedImage.getWidth();
      final int h = bufferedImage.getHeight();

      final int type = bufferedImage.getType();

      switch(type) {
         case TYPE_CUSTOM: {
            // ************************************************************************
            // Let's see if we can still handle this.
            // This will be the case as long as the following is true:
            // - The ColorModel indicates 3 channels (components).
            // - The ColorModel is either DirectColorModel or ComponentColorModel
            // - The ColorSpace type is TYPE_RGB
            checkCustomHandlingPrereq(bufferedImage);
            final ColorModel cm = bufferedImage.getColorModel();
            final int numChannels = cm.getNumComponents();

            // ************************************************
            // determine the number of bits for each channel.
            // and set the flag that indicates whether or not
            // they're all the same.
            final int[] bitsPerChannel = new int[numChannels];
            boolean notAllChannelsTheSame = false;
            {
               int bpc = -1;
               for(int i = 0; i < numChannels; i++) {
                  final int nextBpc = cm.getComponentSize(i);
                  if(bpc == -1)
                     bpc = nextBpc;
                  else if(bpc != nextBpc)
                     notAllChannelsTheSame = true;
                  bitsPerChannel[i] = cm.getComponentSize(i);
               }
            }
            // ************************************************

            if(cm instanceof DirectColorModel) {
               final DirectColorModel dcm = (DirectColorModel)cm;
               // we need to split the image into a set of images per channel.
               try (final Closer closer = new Closer();) {
                  try (final CvMat rawMat = putDataBufferIntoMat(bufferedImage.getRaster().getDataBuffer(), h, w, 1);
                        final CvMat maskMat = new CvMat(1, 1, CvType.makeType(rawMat.depth(), 1));
                        final CvMat remergedMat = new CvMat()) {

                     final int[] nBits = cm.getComponentSize();

                     int componentDepth = CV_8U;
                     double maxPixelValue = 256.0D; // Yes, this is 1 more than the MAX value of a byte but we need it
                                                    // that way later.
                     // these all need to be 8 or less, otherwise there's at least one component that wont fit in a
                     // byte.
                     for(int i = 0; i < nBits.length; i++) {
                        final int n = nBits[i];
                        if(n > 16)
                           throw new IllegalArgumentException("Can't handle an image of type " + type
                                 + " with a DirectColorModel when some of the channels wont fit in an unsigned short. The image's bit-per-channel are "
                                 + Arrays.toString(nBits));
                        if(n > 8) {
                           componentDepth = CV_16U;
                           maxPixelValue = 65536.0D;
                           break;
                        }
                     }

                     // we're going to separate the channels into separate Mat's by masking
                     final List<Mat> channels = new ArrayList<>(numChannels);

                     // If the image is 32bits and the MSb is touched by the mask then
                     // These operations don't work right since OpenCV thinks that a
                     // convertTo should handle a sign change by a means other than simply
                     // truncating and at the same time I can't use a CV_32U
                     final boolean[] hacked = new boolean[numChannels];

                     for(int ch = 0; ch < numChannels; ch++) {

                        int mask = dcm.getMask(ch);

                        @SuppressWarnings("resource")
                        Mat matToMask = closer.add(CvMat.shallowCopy(rawMat));
                        if(rawMat.depth() == CvType.CV_32S && mask < 0) {
                           // this is a problem since there's no CV_32U so we need to to an extra mask and shift here.
                           try (CvMat tmpMat = new CvMat();) {
                              hacked[ch] = true;
                              scalarToMat(mask, rawMat.type(), maskMat);
                              Core.bitwise_and(rawMat, maskMat, tmpMat);
                              mask >>>= 1;
                              matToMask = closer.add(new CvMat());
                              Core.multiply(tmpMat, new Scalar(0.5D), matToMask);
                           }
                        }

                        scalarToMat(mask, rawMat.type(), maskMat);

                        final CvMat maskedMat = closer.add(new CvMat());
                        Core.bitwise_and(matToMask, maskMat, maskedMat);
                        channels.add(maskedMat);
                     }

                     // channels should now be set to the R, G, B masked mats but without the bit's shifted.
                     final int[] divisors = determineDivisors(dcm.getMasks());

                     // The channels are now in RGB order but we want them in BGR so
                     // we run the loop this way

                     // TODO: This assumes there's 3 channels.
                     if(numChannels != 3)
                        throw new IllegalArgumentException("Can't handle an image of type " + type
                              + " with a DirectColorModel with " + numChannels + " channels.");
                     final double fmaxPixelValue = maxPixelValue;
                     final int fcomponentDepth = componentDepth;
                     final List<Mat> newChannelOrder = Arrays.stream(new int[] {2,1,0}) // <- 3 channel assumption
                           .mapToObj(ch -> {
                              final int divisor = hacked[ch] ? (divisors[ch] >> 1) : divisors[ch];
                              final Mat cur = channels.get(ch);
                              final double numerator = (nBits[ch] == 0) ? fmaxPixelValue : ((fmaxPixelValue - 1) / ((1 << nBits[ch]) - 1));

                              final double factor = numerator / divisor;
                              cur.convertTo(cur, fcomponentDepth, factor);
                              return cur;
                           })
                           .collect(Collectors.toList());

                     // now merge the channels
                     Core.merge(newChannelOrder, remergedMat);
                     return remergedMat.returnMe();
                  }
               }
            } else {
               if(notAllChannelsTheSame)
                  throw new IllegalArgumentException("Cannot handle images where the channels have a different number of bits per channel. They are currently: "
                        + Arrays.toString(bitsPerChannel));

               final int bpc = bitsPerChannel[0];

               // Now, do we have an 8-bit RGB or a 16-bit (per-channel) RGB
               if(bpc > 8 && bpc <= 16) {
                  final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
                  // make sure the DataBuffer type is a DataBufferUShort
                  if(!(dataBuffer instanceof DataBufferUShort))
                     throw new IllegalArgumentException("For a 16-bit per channel RGB image the DataBuffer type should be a DataBufferUShort but it's a "
                           + dataBuffer.getClass().getSimpleName());
                  try (CvMat ret = rgbDataBufferUShortToMat((DataBufferUShort)dataBuffer, h, w);) {
                     if(bpc != 16) {
                        final double maxPixelValue = 65536.0D;
                        double scale = ((maxPixelValue - 1) / ((1 << bpc) - 1));
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
                  return rgbDataBufferByteToMat((DataBufferByte)dataBuffer, h, w);
               } else
                  throw new IllegalArgumentException(
                        "Cannot handle an image of type " + type + " with a ComponentColorModel that has " + bpc + " bits per channel.");
            }
         }
         case TYPE_INT_RGB: // 8-bit per channel packed into ints
         case TYPE_INT_BGR: // 8-bit per channel packed into ints, BGR order
         case TYPE_INT_ARGB:
         case TYPE_INT_ARGB_PRE: {
            final DataBuffer dataBuffer = bufferedImage.getRaster().getDataBuffer();
            if(!(dataBuffer instanceof DataBufferInt))
               throw new IllegalArgumentException("BufferedImage of type \"" + type + "\" should have a " + DataBufferInt.class.getSimpleName()
                     + " but instead has a " + dataBuffer.getClass().getSimpleName());
            final DataBufferInt bb = (DataBufferInt)dataBuffer;
            int masks[] = null;
            int shift[] = null;
            switch(type) {
               case TYPE_INT_RGB:
                  masks = new int[] {0x0,0x00ff0000,0x0000ff00,0x000000ff};
                  shift = new int[] {24,16,8,0};
                  break;
               case TYPE_INT_BGR:
                  masks = new int[] {0x0,0x000000ff,0x0000ff00,0x00ff0000};
                  shift = new int[] {24,0,8,16};
                  break;
               case TYPE_INT_ARGB:
               case TYPE_INT_ARGB_PRE:
                  masks = new int[] {0xff000000,0x00ff0000,0x0000ff00,0x000000ff};
                  shift = new int[] {24,16,8,0};
                  break;
            }
            return argbDataBufferIntToMat(bb, h, w, masks, shift);
         }
         case TYPE_3BYTE_BGR:
         case TYPE_4BYTE_ABGR:
         case TYPE_4BYTE_ABGR_PRE: {
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
         case TYPE_USHORT_555_RGB: // 15 bit (wtf?) with 5/5/5 r/g/b.
         case TYPE_USHORT_565_RGB: // 16 bit total with 5 bit r, 6 bit g, 5 bit blue
            return handle555and565(bufferedImage, type == TYPE_USHORT_565_RGB);
         default:
            throw new IllegalArgumentException("Cannot extract pixels from a BufferedImage of type " + bufferedImage.getType());
      }
   }

   public static void print(final String prefix,
         final Mat im) {
      System.out
            .println(prefix + " { depth=(" + CvType.ELEM_SIZE(im.type()) + "(" + CvType.typeToString(im.type()) + "), " + im.depth() + "), channels="
                  + im.channels() + " HxW=" + im.height() + "x" + im.width() + " }");
   }

   /**
    * Find the point on the line defined by {@code perpRef} that's closest to the point {@code x}.
    * Note, {@link PerpendicularLine} is poorly named.
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
    * This method will overlay the {@code overlay} image onto the {@code original} image by
    * having the original image show through the overlay, everywhere the overlay has a pixel
    * value of zero (or zero in all channels).
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

   private static CvMat argbDataBufferByteToMat(final DataBufferByte bb, final int h, final int w, final int[] lookup, final int skip) {
      try (final CvMat retMat = new CvMat(h, w, skip == 4 ? CvType.CV_8UC4 : CvType.CV_8UC3);) {
         final byte[] inpixels = bb.getData();
         if(lookup == null) // indicates a pixel compatible format
            retMat.put(0, 0, inpixels);
         else {
            final boolean hasAlpha = lookup[0] != -1;
            final int alpha = lookup[0];
            final int blue = lookup[3];
            final int red = lookup[1];
            final int green = lookup[2];
            final byte[] outpixel = new byte[skip]; // pixel length
            final int colsXchannels = retMat.cols() * retMat.channels();
            retMat.rasterAp(raster -> raster.apply((BytePixelSetter)(r, c) -> {
               final int pos = (r * colsXchannels) + (c * skip);
               outpixel[0] = inpixels[pos + blue];
               outpixel[1] = inpixels[pos + green];
               outpixel[2] = inpixels[pos + red];
               if(hasAlpha)
                  outpixel[3] = inpixels[pos + alpha];
               return outpixel;
            }));
         }
         return retMat.returnMe();
      }
   }

   private static CvMat argbDataBufferByteToMat(final DataBufferInt bi, final int h, final int w, final int[] mask, final int[] shift) {
      final boolean hasAlpha = mask[0] != 0x0;
      final CvMat retMat = new CvMat(h, w, hasAlpha ? CvType.CV_8UC4 : CvType.CV_8UC3);
      final int[] inpixels = bi.getData();
      final int blue = 3;
      final int red = 1;
      final int green = 2;
      final int alpha = 0;
      final byte[] outpixel = new byte[hasAlpha ? 4 : 3]; // pixel length
      final int cols = retMat.cols();
      retMat.rasterAp(raster -> raster.apply((BytePixelSetter)(r, c) -> {
         final int pixel = inpixels[(r * cols) + c];
         outpixel[0] = (byte)((pixel & mask[blue]) >>> shift[blue]);
         outpixel[1] = (byte)((pixel & mask[green]) >>> shift[green]);
         outpixel[2] = (byte)((pixel & mask[red]) >>> shift[red]);
         if(hasAlpha)
            outpixel[3] = (byte)((pixel & mask[alpha]) >>> shift[alpha]);
         return outpixel;
      }));
      return retMat;
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

   private static CvMat argbDataBufferIntToMat(final DataBufferInt bi, final int h, final int w, final int[] mask, final int[] shift) {
      final boolean hasAlpha = mask[0] != 0x0;
      final CvMat retMat = new CvMat(h, w, hasAlpha ? CvType.CV_8UC4 : CvType.CV_8UC3);
      final int[] inpixels = bi.getData();
      final int blue = 3;
      final int red = 1;
      final int green = 2;
      final int alpha = 0;
      final byte[] outpixel = new byte[hasAlpha ? 4 : 3]; // pixel length
      final int cols = retMat.cols();
      retMat.rasterAp(raster -> raster.apply((BytePixelSetter)(r, c) -> {
         final int pixel = inpixels[(r * cols) + c];
         outpixel[0] = (byte)((pixel & mask[blue]) >>> shift[blue]);
         outpixel[1] = (byte)((pixel & mask[green]) >>> shift[green]);
         outpixel[2] = (byte)((pixel & mask[red]) >>> shift[red]);
         if(hasAlpha)
            outpixel[3] = (byte)((pixel & mask[alpha]) >>> shift[alpha]);
         return outpixel;
      }));
      return retMat;
   }

   private static CvMat abgrDataBufferByteToMat(final DataBufferByte bb, final int h, final int w, final boolean hasAlpha) {
      try (final CvMat retMat = new CvMat(h, w, hasAlpha ? CvType.CV_8UC4 : CvType.CV_8UC3);) {
         final byte[] inpixels = bb.getData();
         retMat.put(0, 0, inpixels);
         if(!hasAlpha) { // indicates a pixel compatible format since the only option is TYPE_3BYTE_BGR
            return retMat.returnMe();
         } else { // then it's ABGR -> BGRA
            try (final CvMat reshaped = CvMat.move(retMat.reshape(1, h * w));
                  // type to 32F so we can multiple it by the matrix. It would be nice of there was an integer 'gemm'
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

   private static CvMat rgbDataBufferUShortToMat(final DataBufferUShort bb, final int h, final int w) {
      try (final CvMat mat = new CvMat(h, w, CvType.CV_16UC3);) {
         final short[] inpixels = bb.getData();
         mat.put(0, 0, inpixels);
         try (CvMat ret = Functional.chain(new CvMat(), m -> Imgproc.cvtColor(mat, m, Imgproc.COLOR_RGB2BGR));) {
            return ret.returnMe();
         }
      }
   }

   private static CvMat rgbDataBufferByteToMat(final DataBufferByte bb, final int h, final int w) {
      try (final CvMat mat = new CvMat(h, w, CvType.CV_8UC3);) {
         final byte[] inpixels = bb.getData();
         mat.put(0, 0, inpixels);
         try (CvMat ret = Functional.chain(new CvMat(), m -> Imgproc.cvtColor(mat, m, Imgproc.COLOR_RGB2BGR));) {
            return ret.returnMe();
         }
      }
   }
}
