package ai.kognition.pilecv4j.image;

import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.opencv.core.CvType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class UtilsForTesting {

   public static final Map<Integer, Integer> biTypeToPixDelta = new HashMap<>();

   static {
      biTypeToPixDelta.put(TYPE_USHORT_555_RGB, 10);
      biTypeToPixDelta.put(TYPE_USHORT_565_RGB, 10);
   }

   public static String translateClasspath(final String classpathPath) {
      return new File(TestUtils.class.getClassLoader().getResource(classpathPath).getFile()).getAbsolutePath();
   }

   public static void compare(final CvMat mat, final BufferedImage im) {
      final Integer pixDeltaInt = biTypeToPixDelta.get(im.getType());
      final int pixDelta = pixDeltaInt == null ? 1 : pixDeltaInt.intValue();

      final boolean hasAlpha = im.getColorModel().hasAlpha();
      final WritableRaster biraster = im.getRaster();
      mat.rasterAp(raster -> {
         final Function<Object, int[]> pixelToInt = CvRaster.pixelToIntsConverter(raster.type());

         for(int r = 0; r < mat.rows(); r++) {
            for(int c = 0; c < mat.cols(); c++) {
               // check that the number of bands equals
               final int matChannels = raster.channels();
               final int channels = biraster.getNumBands();
               assertEquals(channels, matChannels);

               final int[] rgb = new int[3];
               int alpha = -1;

               {
                  final Color biPixel;
                  if(im.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
                     final Object arr = im.getRaster().getDataElements(c, r, null);
                     if(arr instanceof byte[]) {
                        final byte[] pix = (byte[])arr;
                        biPixel = new Color(pix[0] & 0xff, pix[0] & 0xff, pix[0] & 0xff);
                     } else { // assume short
                        biPixel = null;
                        final short[] pix = (short[])arr;
                        rgb[0] = pix[0] & 0xffff;
                     }
                  } else
                     biPixel = new Color(im.getRGB(c, r), hasAlpha);

                  if(biPixel != null) {
                     rgb[0] = biPixel.getRed();
                     rgb[1] = biPixel.getGreen();
                     rgb[2] = biPixel.getBlue();
                     if(hasAlpha)
                        alpha = biPixel.getAlpha();
                  }
               }

               int[] bpp = im.getColorModel().getComponentSize();
               int[] shift = new int[bpp.length];
               for(int ch = 0; ch < bpp.length; ch++)
                  shift[ch] = bpp[ch] <= 8 ? ((mat.depth() == CvType.CV_16U) ? 8 : 0) : 0;

               final int[] matPixel = pixelToInt.apply(raster.get(r, c));
               if(channels == 1) {
                  assertEquals(rgb[0], matPixel[0] >>> shift[0], pixDelta);
               } else {
                  assertEquals(rgb[0], matPixel[2] >>> shift[0], pixDelta);
                  assertEquals(rgb[1], matPixel[1] >>> shift[1], pixDelta);
                  assertEquals(rgb[2], matPixel[0] >>> shift[2], pixDelta);
                  if(channels > 3)
                     assertEquals(alpha, matPixel[3] >>> shift[3], pixDelta);
               }
            }
         }
      });
   }

   public static void compare(final BufferedImage biA, final BufferedImage biB) {
      if(biTypeToPixDelta.containsKey(biA.getType()) || biTypeToPixDelta.containsKey(biB.getType()))
         return; // can't check if we expect a delta.

      // take buffer data from botm image files //
      final int w = biA.getWidth();
      final int h = biA.getHeight();
      assertEquals(w, biB.getWidth());
      assertEquals(h, biB.getHeight());

      final int[] pixelsA = biA.getRGB(0, 0, w, h, null, 0, w);
      final int[] pixelsB = biB.getRGB(0, 0, w, h, null, 0, w);
      assertEquals(pixelsA.length, pixelsB.length);
      assertEquals(w * h, pixelsA.length);

      assertArrayEquals(pixelsA, pixelsB);
   }

}
