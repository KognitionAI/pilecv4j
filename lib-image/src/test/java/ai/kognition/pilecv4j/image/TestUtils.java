package ai.kognition.pilecv4j.image;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;

import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.swing.SwingImageDisplay;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import net.dempsy.util.QuietCloseable;

public class TestUtils {

   final String testImageFilename = new File(
         getClass().getClassLoader().getResource("test-images/people.jpeg").getFile()).getAbsolutePath();

   public final static boolean SHOW = CvRasterTest.SHOW;

   public static void compare(final CvMat mat, final BufferedImage im) {
      final boolean oneChannel = mat.channels() == 1;
      final WritableRaster biraster = im.getRaster();
      mat.rasterAp(raster -> {
         for(int r = 0; r < mat.rows(); r++) {
            for(int c = 0; c < mat.cols(); c++) {
               if(!oneChannel) {
                  final byte[] bipix = new byte[3];
                  bipix[0] = (byte)biraster.getSample(c, r, 2);
                  bipix[1] = (byte)biraster.getSample(c, r, 1);
                  bipix[2] = (byte)biraster.getSample(c, r, 0);

                  final byte[] pix = (byte[])raster.get(r, c);
                  assertArrayEquals(pix, bipix);
               } else {
                  final byte[] bipix = new byte[1];
                  bipix[0] = (byte)biraster.getSample(c, r, 0);

                  final byte[] pix = (byte[])raster.get(r, c);
                  assertArrayEquals(pix, bipix);
               }
            }
         }
      });
   }

   @Test
   public void testMatToImg() throws Exception {
      try (CvMat mat = ImageFile.readMatFromFile(testImageFilename);) {
         final BufferedImage im = Utils.mat2Img(mat);
         if(SHOW) {
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).build();
                  QuietCloseable c = SwingImageDisplay.showImage(im);) {
               Thread.sleep(3000);
            }
         }
         compare(mat, im);
      }
   }

   @Test
   public void testMatToImgGray() throws Exception {
      try (CvMat omat = ImageFile.readMatFromFile(testImageFilename);
            CvMat mat = Operations.convertToGray(omat);) {
         final BufferedImage im = Utils.mat2Img(mat);
         Imgproc.cvtColor(omat, mat, Imgproc.COLOR_BGR2GRAY);
         if(SHOW) {
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).build();
                  QuietCloseable c = SwingImageDisplay.showImage(im);) {
               Thread.sleep(3000);
            }
         }
         compare(mat, im);
      }
   }

   @Test
   public void testFakeMatToImg() throws Exception {
      try (CvMat mat = new CvMat(255, 255, CvType.CV_8UC3);) {
         mat.rasterAp(raster -> {
            raster.apply((BytePixelSetter)(r, c) -> new byte[] {0,0,(byte)c});
         });
         final BufferedImage im = Utils.mat2Img(mat);

         if(SHOW) {
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).build();
                  QuietCloseable c = SwingImageDisplay.showImage(im);) {
               Thread.sleep(3000);
            }
         }
         compare(mat, im);
      }
   }

   @Test
   public void testImgToMat() throws Exception {
      final BufferedImage im = ImageFile.readBufferedImageFromFile(testImageFilename);
      try (final CvMat mat = Utils.img2CvMat(im);) {
         if(SHOW) {
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).build();
                  QuietCloseable c = SwingImageDisplay.showImage(im)) {
               Thread.sleep(3000);
            }
         }
         compare(mat, im);
      }
   }

   @Test
   public void testImgToMatGray() throws Exception {
      // generate a gray scale BufferedImage
      final BufferedImage im = new BufferedImage(255, 255, BufferedImage.TYPE_BYTE_GRAY);
      final byte[] buf = ((DataBufferByte)im.getRaster().getDataBuffer()).getData();
      for(int r = 0; r < 255; r++) {
         final int rowPos = r * 255;
         for(int c = 0; c < 255; c++)
            buf[rowPos + c] = (byte)c;
      }

      try (final CvMat mat = Utils.img2CvMat(im);) {
         if(SHOW) {
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).build();
                  QuietCloseable c = SwingImageDisplay.showImage(im)) {
               Thread.sleep(3000);
            }
         }
         assertEquals(1, mat.channels());
         compare(mat, im);
      }
   }
}
