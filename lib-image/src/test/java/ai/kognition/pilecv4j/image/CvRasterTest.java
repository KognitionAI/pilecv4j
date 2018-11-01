package ai.kognition.pilecv4j.image;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.GetChannelValueAsInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

public class CvRasterTest {

   public final static boolean SHOW = false; /// can only set this to true when building on a machine with a display

   static {
      CvMat.initOpenCv();
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
      if(SHOW) {
         try (final CvMat raster = ImageFile.readMatFromFile(testImagePath);
               QuietCloseable c = new ImageDisplay.Builder()
                     .show(raster).windowName("Test").build();) {
            Thread.sleep(5000);
         }
      }
   }

   @Test
   public void testSimpleCreate() throws Exception {
      final String expectedFileLocation = testImagePath;

      try (final CvMat mat = new CvMat(IMAGE_WIDTH_HEIGHT, IMAGE_WIDTH_HEIGHT, CvType.CV_8UC1)) {
         mat.rasterAp(raster -> {
            raster.apply((BytePixelSetter)(r, c) -> new byte[] {(byte)(((r + c) >> 1) & 0xff)});

            try (final CvMat expected = Functional.uncheck(() -> ImageFile.readMatFromFile(expectedFileLocation));) {
               expected.rasterAp(e -> assertEquals(e, raster));
            }
         });
      }
   }

   @Test
   public void testEqualsAndNotEquals() throws Exception {
      final String expectedFileLocation = testImagePath;

      try (final CvMat omat = new CvMat(IMAGE_WIDTH_HEIGHT, IMAGE_WIDTH_HEIGHT, CvType.CV_8UC1)) {
         omat.rasterAp(ra -> ra.apply((BytePixelSetter)(r, c) -> {
            if(r == 134 && c == 144)
               return new byte[] {-1};
            return new byte[] {(byte)(((r + c) >> 1) & 0xff)};
         }));

         try (final CvMat emat = ImageFile.readMatFromFile(expectedFileLocation);) {
            emat.rasterAp(expected -> omat.rasterAp(raster -> {
               assertNotEquals(expected, raster);

               // correct the pixel
               raster.set(134, 144, new byte[] {(byte)(((134 + 144) >> 1) & 0xff)});
               assertEquals(expected, raster);
            }));
         }
      }
   }

   @Test
   public void testReduce() throws Exception {
      try (final CvMat mat = new CvMat(255, 255, CvType.CV_8UC1);) {
         mat.rasterAp(raster -> {
            raster.apply((BytePixelSetter)(r, c) -> new byte[] {(byte)c});
         });

         final GetChannelValueAsInt valueFetcher = CvRaster.channelValueFetcher(mat.type());
         final long sum = CvMat.rasterOp(mat,
               raster -> raster.reduce(Long.valueOf(0), (prev, pixel, row, col) -> Long.valueOf(prev.longValue() + valueFetcher.get(pixel, 0))));

         long expected = 0;
         for(int i = 0; i < 255; i++)
            expected = expected + i;
         expected *= 255;

         assertEquals(expected, sum);
      }
   }
}
