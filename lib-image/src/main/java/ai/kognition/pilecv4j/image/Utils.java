package ai.kognition.pilecv4j.image;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;
import static org.opencv.core.CvType.CV_16S;
import static org.opencv.core.CvType.CV_16U;
import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8U;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.io.PrintStream;
import java.lang.reflect.Array;

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
import ai.kognition.pilecv4j.image.CvRaster.DoublePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.FlatDoublePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.FloatPixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.IntPixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.PixelConsumer;
import ai.kognition.pilecv4j.image.CvRaster.ShortPixelConsumer;
import ai.kognition.pilecv4j.image.geometry.PerpendicularLine;
import ai.kognition.pilecv4j.image.geometry.Point;
import ai.kognition.pilecv4j.image.geometry.SimplePoint;

import net.dempsy.util.QuietCloseable;

public class Utils {
   public static final ColorModel grayColorModel;

   static {
      final ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
      final int[] nBits = {8};
      grayColorModel = new ComponentColorModel(cs, nBits, false, true, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
   }

   public static BufferedImage mat2Img(final Mat in) {
      final int inChannels = in.channels();
      if(inChannels == 1) { // assume gray
         final int type;

         switch(CvType.depth(in.type())) {
            case CV_8U:
            case CV_8S:
               type = BufferedImage.TYPE_BYTE_GRAY;
               break;
            case CV_16U:
            case CV_16S:
               type = BufferedImage.TYPE_USHORT_GRAY;
               break;
            default:
               throw new IllegalArgumentException(
                     "Cannot convert a Mat with a type of " + CvType.typeToString(in.type()) + " to a BufferedImage");
         }

         final BufferedImage out = new BufferedImage(in.width(), in.height(), type);
         CvRaster.copyToPrimitiveArray(in, ((DataBufferByte)out.getRaster().getDataBuffer()).getData());
         return out;
      } else if(inChannels == 3) {
         final int cvDepth = CvType.depth(in.type());
         if(cvDepth != CV_8U && cvDepth != CV_8S)
            throw new IllegalArgumentException("Cannot convert RGB Mats with elements larger than a byte yet.");

         final BufferedImage out = new BufferedImage(in.width(), in.height(), BufferedImage.TYPE_3BYTE_BGR);
         CvRaster.copyToPrimitiveArray(in, ((DataBufferByte)out.getRaster().getDataBuffer()).getData());
         return out;
      } else if(inChannels == 4)
         throw new IllegalArgumentException("Can't handle alpha channel yet.");
      else
         throw new IllegalArgumentException("Can't handle an image with " + inChannels + " channels");

   }

   public static BufferedImage mat2Img(final Mat in, final IndexColorModel colorModel) {
      BufferedImage out;

      if(in.channels() != 1 || CvType.depth(in.type()) != CvType.CV_8U)
         throw new IllegalArgumentException("Cannot convert a Mat to a BufferedImage with a colorMap if the Mat has more than one channel);");

      out = new BufferedImage(in.cols(), in.rows(), BufferedImage.TYPE_BYTE_INDEXED, colorModel);

      out.getRaster().setDataElements(0, 0, in.cols(), in.rows(), CvRaster.copyToPrimitiveArray(in));
      return out;
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

   @SuppressWarnings("unchecked")
   public static void dump(final CvRaster raster, final PrintStream out) {
      out.println(raster.mat);
      @SuppressWarnings("rawtypes")
      final PixelConsumer pp = CvRaster.makePixelPrinter(out, raster.type());
      for(int r = 0; r < raster.rows(); r++) {
         out.print("[");
         for(int c = 0; c < raster.cols() - 1; c++) {
            out.print(" ");
            pp.accept(r, c, raster.get(r, c));
            out.print(",");
         }
         out.print(" ");
         pp.accept(r, raster.cols() - 1, raster.get(r, raster.cols() - 1));
         out.println("]");
      }
   }

   public static void dump(final Mat mat) {
      dump(mat, System.out);
   }

   public static void dump(final Mat mat, final PrintStream out) {
      CvMat.rasterAp(mat, raster -> dump(raster, out));
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

   public static CvMat img2CvMat(final BufferedImage crappyImage) {
      final int w = crappyImage.getWidth();
      final int h = crappyImage.getHeight();

      final int type = crappyImage.getType();

      switch(type) {
         case TYPE_INT_RGB: // 8-bit per channel packed into ints
         case TYPE_INT_BGR: // 8-bit per channel packed into ints, BGR order
         case TYPE_INT_ARGB:
         case TYPE_INT_ARGB_PRE: {
            final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
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
               case TYPE_3BYTE_BGR:
               case TYPE_INT_BGR:
                  masks = new int[] {0x0,0x000000ff,0x0000ff00,0x00ff0000};
                  shift = new int[] {24,0,8,16};
                  break;
               case TYPE_INT_ARGB:
               case TYPE_INT_ARGB_PRE:
                  masks = new int[] {0xff000000,0x00ff0000,0x0000ff00,0x000000ff};
                  shift = new int[] {24,16,8,0};
                  break;
               case TYPE_4BYTE_ABGR:
               case TYPE_4BYTE_ABGR_PRE:
                  masks = new int[] {0xff000000,0x000000ff,0x0000ff00,0x00ff0000};
                  shift = new int[] {24,0,8,16};
                  break;
            }
            return argbDataBufferByteToMat(bb, h, w, masks, shift);
         }
         case TYPE_3BYTE_BGR:
         case TYPE_4BYTE_ABGR:
         case TYPE_4BYTE_ABGR_PRE: {
            final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
            if(!(dataBuffer instanceof DataBufferByte))
               throw new IllegalArgumentException("BufferedImage of type \"" + type + "\" should have a " + DataBufferByte.class.getSimpleName()
                     + " but instead has a " + dataBuffer.getClass().getSimpleName());
            final DataBufferByte bb = (DataBufferByte)dataBuffer;
            int[] lookup = null;
            int skip = -1;
            switch(type) {
               case TYPE_INT_RGB:
                  lookup = new int[] {-1,0,1,2};
                  skip = 4;
                  break;
               case TYPE_3BYTE_BGR:
                  skip = 3;
                  lookup = null; // straight copy since it already matches.
                  break;
               case TYPE_INT_BGR:
                  skip = 4;
                  lookup = new int[] {-1,2,1,0};
                  break;
               case TYPE_INT_ARGB:
               case TYPE_INT_ARGB_PRE:
                  lookup = new int[] {0,1,2,3};
                  skip = 4;
                  break;
               case TYPE_4BYTE_ABGR:
               case TYPE_4BYTE_ABGR_PRE:
                  lookup = new int[] {0,3,2,1};
                  skip = 4;
                  break;
            }
            return argbDataBufferByteToMat(bb, h, w, lookup, skip);
         }
         case TYPE_BYTE_GRAY: {
            final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
            if(!(dataBuffer instanceof DataBufferByte))
               throw new IllegalArgumentException("BufferedImage should have a " + DataBufferByte.class.getSimpleName() + " but instead has a "
                     + dataBuffer.getClass().getSimpleName());
            final DataBufferByte bb = (DataBufferByte)dataBuffer;
            final byte[] srcdata = bb.getData();
            final CvMat ret = new CvMat(h, w, CvType.CV_8UC1);
            ret.put(0, 0, srcdata);
            return ret;
         }
         case TYPE_USHORT_GRAY: {
            final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
            if(!(dataBuffer instanceof DataBufferUShort))
               throw new IllegalArgumentException("BufferedImage should have a " + DataBufferUShort.class.getSimpleName() + " but instead has a "
                     + dataBuffer.getClass().getSimpleName());
            final DataBufferUShort bb = (DataBufferUShort)dataBuffer;
            final short[] srcdata = bb.getData();
            final CvMat ret = new CvMat(h, w, CvType.CV_16UC1);
            ret.put(0, 0, srcdata);
            return ret;
         }
         case TYPE_USHORT_565_RGB: // 16 bit total with 5 bit r, 6 bit g, 5 bit blue
         case TYPE_USHORT_555_RGB: // 15 bit (wtf?) with 5/5/5 r/g/b.
            throw new IllegalArgumentException("Cannot handle a BufferedImage with 5/6/5 RGB 16-bit packed pixels. Sorry. Ask nice maybe I'll fix it.");
         default:
            throw new IllegalArgumentException("Cannot extract pixels from a BufferedImage of type " + crappyImage.getType());
      }
   }

   public static void print(final String prefix, final Mat im) {
      System.out.println(prefix + " { depth=(" + CvType.ELEM_SIZE(im.type()) + "(" + CvType.typeToString(im.type()) + "), " + im.depth() + "), channels="
            + im.channels() + " HxW=" + im.height() + "x" + im.width() + " }");
   }

   public static Point closest(final Point x, final PerpendicularLine perpRef) {
      return closest(x, perpRef.x(), perpRef.y());
   }

   static private Point closest(final Point x, final double perpRefX, final double perpRefY) {
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

   static public void drawPolarLine(final double r, final double c, final Mat ti, final Color color) {
      drawPolarLine(r, c, ti, color, 0, 0, ti.rows() - 1, ti.cols() - 1);
   }

   static public void drawPolarLine(final double r, final double c, final Mat ti, final Color color,
         final int boundingr1, final int boundingc1, final int boundingr2, final int boundingc2) {
      drawPolarLine(r, c, ti, color, boundingr1, boundingc1, boundingr2, boundingc2, 0, 0);
   }

   static public void drawPolarLine(final double r, final double c, final Mat ti, final Color color,
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

   public static Size perserveAspectRatio(final Mat mat, final Size newSize) {
      return perserveAspectRatio(mat.size(), newSize);

   }

   public static Size perserveAspectRatio(final Size originalMatSize, final Size newSize) {
      // calculate the appropriate resize
      final double fh = newSize.height / originalMatSize.height;
      final double fw = newSize.width / originalMatSize.width;
      final double scale = fw < fh ? fw : fh;
      return (scale >= 1.0) ? new Size(originalMatSize.width, originalMatSize.height)
            : new Size(Math.round(originalMatSize.width * scale), Math.round(originalMatSize.height * scale));

   }
}
