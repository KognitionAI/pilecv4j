package com.jiminger.image;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import com.jiminger.image.CvRaster.BytePixelSetter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import net.dempsy.util.QuietCloseable;

public class CvRasterTest {

   static {
      CvRaster.initOpenCv();
   }

   private static String testImagePath = new File(
         CvRasterTest.class.getClassLoader().getResource("test-images/expected-8bit-grey.darkToLight.bmp").getFile())
               .getPath();

   private static int IMAGE_WIDTH_HEIGHT = 256;

   @Rule
   public TemporaryFolder tempDir = new TemporaryFolder();

   @Test
   public void testMove() throws Exception {
      try (final CvMat cvmat = scopedGetAndMove();) {
         assertEquals(IMAGE_WIDTH_HEIGHT, cvmat.rows());
         assertEquals(IMAGE_WIDTH_HEIGHT, cvmat.cols());

         // meeger attempt to call the finalizer on the original Mat
         // created in scopedGetAndMove using Imgcodecs.
         for(int i = 0; i < 10; i++) {
            System.gc();
            Thread.sleep(100);
         }
      }
   }

   /*
    * This is here because it's part of the above testing of the
    * Mat 'move' functionality.
    */
   private static CvMat scopedGetAndMove() {
      final Mat mat = Imgcodecs.imread(testImagePath, IMREAD_UNCHANGED);
      assertEquals(IMAGE_WIDTH_HEIGHT, mat.rows());
      assertEquals(IMAGE_WIDTH_HEIGHT, mat.cols());
      final CvMat ret = CvMat.move(mat);
      assertEquals(0, mat.rows());
      assertEquals(0, mat.cols());
      return ret;
   }

   @Test
   public void testShow() throws Exception {
      try (final CvRaster raster = ImageFile.readMatFromFile(testImagePath);
            QuietCloseable c = raster.show("Test");) {
         Thread.sleep(5000);
      }
   }

   @Test
   public void testSimpleCreate() throws Exception {
      final String expectedFileLocation = testImagePath;

      try (final CvRaster raster = CvRaster.create(IMAGE_WIDTH_HEIGHT, IMAGE_WIDTH_HEIGHT, CvType.CV_8UC1)) {
         raster.apply((BytePixelSetter)(r, c) -> new byte[] {(byte)(((r + c) >> 1) & 0xff)});

         final CvRaster expected = ImageFile.readMatFromFile(expectedFileLocation);
         assertEquals(expected, raster);
      }
   }

   @Test
   public void testEqualsAndNotEquals() throws Exception {
      final String expectedFileLocation = testImagePath;

      try (final CvRaster raster = CvRaster.create(IMAGE_WIDTH_HEIGHT, IMAGE_WIDTH_HEIGHT, CvType.CV_8UC1)) {
         raster.apply((BytePixelSetter)(r, c) -> {
            if(r == 134 && c == 144)
               return new byte[] {-1};
            return new byte[] {(byte)(((r + c) >> 1) & 0xff)};
         });

         final CvRaster expected = ImageFile.readMatFromFile(expectedFileLocation);
         assertNotEquals(expected, raster);

         // correct the pixel
         raster.set(134, 144, new byte[] {(byte)(((134 + 144) >> 1) & 0xff)});
         assertEquals(expected, raster);
      }
   }

}
