package ai.kognition.pilecv4j.image;

import java.awt.Color;
import java.awt.image.IndexColorModel;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvRaster.Closer;

public class Operations {
   private static Logger LOGGER = LoggerFactory.getLogger(Operations.class);

   static {
      CvMat.initOpenCv();
   }

   public static byte EDGE = (byte)-1;
   public static final byte ROVERLAY = (byte)100;
   public static final byte GOVERLAY = (byte)101;
   public static final byte BOVERLAY = (byte)102;
   public static final byte YOVERLAY = (byte)103;
   public static final byte COVERLAY = (byte)104;
   public static final byte MOVERLAY = (byte)105;
   public static final byte OOVERLAY = (byte)106;
   public static final byte GRAYOVERLAY = (byte)107;

   private static final double[] cvrtScaleDenom = new double[6];

   public static final double _256Ov2Pi = (256.0 / (2.0 * Math.PI));

   static {
      cvrtScaleDenom[CvType.CV_16U] = (0xffff);
      cvrtScaleDenom[CvType.CV_16S] = (0x7fff);
      cvrtScaleDenom[CvType.CV_8U] = (0xff);
      cvrtScaleDenom[CvType.CV_8S] = (0x7f);
   }

   public static class GradientImages implements AutoCloseable {
      public final CvMat gradientDir;
      public final CvMat dx;
      public final CvMat dy;

      private GradientImages(final CvMat gradientDir, final CvMat dx, final CvMat dy) {
         this.gradientDir = gradientDir;
         this.dx = dx;
         this.dy = dy;
      }

      @Override
      public void close() throws Exception {
         gradientDir.close();
         dy.close();
         dx.close();
      }
   }

   public static CvMat canny(final GradientImages gis, final double tlow, final double thigh, final Closer closer) {
      try (final CvMat edgeImage = new CvMat();) {
         Imgproc.Canny(gis.dx, gis.dy, edgeImage, tlow, thigh, true);
         return edgeImage.returnMe();
      }
   }

   public static GradientImages gradient(final CvMat grayImage, final int kernelSize, final Closer closerp) {

      // find gradient image
      try (final CvMat dx = new CvMat();
            final CvMat dy = new CvMat();) {
         Imgproc.Sobel(grayImage, dx, CvType.CV_16S, 1, 0, kernelSize, 1.0, 0.0, Core.BORDER_REPLICATE);
         Imgproc.Sobel(grayImage, dy, CvType.CV_16S, 0, 1, kernelSize, 1.0, 0.0, Core.BORDER_REPLICATE);
         final int numPixelsInGradient = dx.rows() * dx.cols();
         final byte[] dirsa = new byte[numPixelsInGradient];

         final short[] tmpdx = new short[numPixelsInGradient];
         dx.get(0, 0, tmpdx);

         dx.rasterAp(dxr -> {
            dy.rasterAp(dyr -> {
               for(int pos = 0; pos < numPixelsInGradient; pos++) {
                  // calculate the angle
                  final double dxv = ((short[])dxr.get(pos))[0];
                  final double dyv = 0.0 - ((short[])dyr.get(pos))[0]; // flip y axis.
                  dirsa[pos] = angle_byte(dxv, dyv);
               }
            });
         });

         // a byte raster to hold the dirs
         try (final CvMat gradientDirImage = new CvMat(dx.rows(), dx.cols(), CvType.CV_8UC1);) {
            gradientDirImage.put(0, 0, dirsa);
            final GradientImages ret = new GradientImages(gradientDirImage.returnMe(), dx.returnMe(), dy.returnMe());
            return closerp == null ? ret : closerp.add(ret);
         }
      }
   }

   public static CvMat convertToGray(final CvMat src) {
      final CvMat workingImage = new CvMat();
      if(src.depth() != CvType.CV_8U) {
         LOGGER.debug("converting image to 8-bit grayscale ... ");
         src.convertTo(workingImage, CvType.CV_8U, 255.0 / cvrtScaleDenom[src.depth()]);
         Imgproc.cvtColor(workingImage, workingImage, Imgproc.COLOR_BGR2GRAY);
         return workingImage;
      } else {
         src.copyTo(workingImage);
         Imgproc.cvtColor(src, workingImage, Imgproc.COLOR_BGR2GRAY);
         return workingImage;
      }
   }

   public static IndexColorModel getOverlayCM() {
      final byte[] r = new byte[256];
      final byte[] g = new byte[256];
      final byte[] b = new byte[256];

      r[intify(EDGE)] = g[intify(EDGE)] = b[intify(EDGE)] = -1;

      r[intify(ROVERLAY)] = -1;
      g[intify(GOVERLAY)] = -1;
      b[intify(BOVERLAY)] = -1;

      r[intify(YOVERLAY)] = -1;
      g[intify(YOVERLAY)] = -1;

      r[intify(COVERLAY)] = byteify(Color.cyan.getRed());
      g[intify(COVERLAY)] = byteify(Color.cyan.getGreen());
      b[intify(COVERLAY)] = byteify(Color.cyan.getBlue());

      r[intify(MOVERLAY)] = byteify(Color.magenta.getRed());
      g[intify(MOVERLAY)] = byteify(Color.magenta.getGreen());
      b[intify(MOVERLAY)] = byteify(Color.magenta.getBlue());

      r[intify(OOVERLAY)] = byteify(Color.orange.getRed());
      g[intify(OOVERLAY)] = byteify(Color.orange.getGreen());
      b[intify(OOVERLAY)] = byteify(Color.orange.getBlue());

      r[intify(GRAYOVERLAY)] = byteify(Color.gray.getRed());
      g[intify(GRAYOVERLAY)] = byteify(Color.gray.getGreen());
      b[intify(GRAYOVERLAY)] = byteify(Color.gray.getBlue());

      return new IndexColorModel(8, 256, r, g, b);
   }

   public static byte angle_byte(final double x, final double y) {
      double xu, yu, ang;
      double ret;
      int rret;

      xu = Math.abs(x);
      yu = Math.abs(y);

      if((xu == 0) && (yu == 0))
         return(0);

      ang = Math.atan(yu / xu);

      if(x >= 0) {
         if(y >= 0)
            ret = ang;
         else
            ret = (2.0 * Math.PI - ang);
      } else {
         if(y >= 0)
            ret = (Math.PI - ang);
         else
            ret = (Math.PI + ang);
      }

      rret = (int)(0.5 + (ret * _256Ov2Pi));
      if(rret >= 256)
         rret = 0;

      return byteify(rret);
   }

   private static byte byteify(final int i) {
      return i > 127 ? (byte)(i - 256) : (byte)i;
   }

   private static int intify(final byte b) {
      return (b < 0) ? (b) + 256 : (int)b;
   }

}
