package ai.kognition.pilecv4j.image;

import static ai.kognition.pilecv4j.image.UtilsForTesting.compare;
import static ai.kognition.pilecv4j.image.UtilsForTesting.translateClasspath;
import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_CUSTOM;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.Implementation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.dempsy.util.Functional;

public class TestUtils {

   final String testImageFilename = translateClasspath("test-images/people.jpeg");

   // public final static boolean SHOW = CvRasterTest.SHOW;
   public final static boolean SHOW = true;

   @Test
   public void testMatToImg() throws Exception {
      try (CvMat mat = ImageFile.readMatFromFile(testImageFilename);) {
         final BufferedImage im = Utils.mat2Img(mat);
         if(SHOW) {
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                  final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
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
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                  final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
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
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                  final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
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
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                  final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
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
            try (final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                  final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
               Thread.sleep(3000);
            }
         }
         assertEquals(1, mat.channels());
         compare(mat, im);
      }
   }

   /**
    * This is the list of types we can handle
    */
   final private static Set<Integer> typesWereTesting = new HashSet<>(Arrays.asList(new Integer[] {
      TYPE_CUSTOM, // 0 - 16-bit RGB works
      TYPE_INT_RGB, // 1 -TYPE_INT_RGB
      // 2, TYPE_INT_ARGB - No samples load to this format
      // 3, TYPE_INT_ARGB_PRE - No samples load to this format
      // 4, TYPE_INT_BGR - No samples load to this format
      TYPE_3BYTE_BGR, // 5
      TYPE_4BYTE_ABGR, // 6
      // 7, TYPE_4BYTE_ABGR_PRE - No samples load to this format
      TYPE_USHORT_565_RGB, // 8
      TYPE_USHORT_555_RGB, // 9
      TYPE_BYTE_GRAY, // 10
      TYPE_USHORT_GRAY // 11
      // 12, TYPE_BYTE_BINARY - Can't handle this yet.
      // 13, TYPE_BYTE_INDEXED - Can't handle this yet.
   }));

   @Test
   public void testConversions() throws Exception {
      final String testImg = translateClasspath("test-images/types");

      try (final ImageDisplay id = SHOW ? new ImageDisplay.Builder().implementation(Implementation.SWT).build() : null;) {
         final List<File> allFiles = Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs));

         allFiles.stream()
               .forEach(imageFile -> {
                  checkConvert(imageFile, id);
                  checkConvert(imageFile.getAbsolutePath(), id);
               });
      }
   }

   private static void findAll(final File file, final List<File> files) {
      if(file.isDirectory())
         Arrays.stream(file.list()).forEach(f -> findAll(new File(file, f), files));
      else
         files.add(file);
   }

   private static void checkConvert(final File imgFile, final ImageDisplay id) {
      final BufferedImage img = Functional.uncheck(() -> ImageFile.readBufferedImageFromFile(imgFile.getAbsolutePath()));

      if(!typesWereTesting.contains(img.getType()))
         return;

      try (CvMat mat = Utils.img2CvMat(img);) {

         if(id != null) {
            id.update(mat);
            Functional.uncheck(() -> Thread.sleep(500));
         }

         compare(mat, img);

         // convert back and compare
         if(mat.depth() == CvType.CV_8U || mat.depth() == CvType.CV_8S) {
            final BufferedImage img2 = Utils.mat2Img(mat);
            compare(img, img2);
         }
      }
   }

   private static void checkConvert(final String imgFile, final ImageDisplay id) {
      try (final CvMat mat = Functional.uncheck(() -> ImageFile.readMatFromFile(imgFile));) {

         if(mat == null) // we couldn't read it yet.
            return;

         if(id != null) {
            id.update(mat);
            Functional.uncheck(() -> Thread.sleep(500));
         }

         // can't convert mat2Img (a)RGB images with components larger than 8-bits.
         if(((mat.channels() == 1 && mat.depth() != CvType.CV_32F && mat.depth() != CvType.CV_64F)
               || mat.depth() == CvType.CV_8U
               || mat.depth() == CvType.CV_8S)
               && mat.channels() != 2) {
            final BufferedImage img = Utils.mat2Img(mat);
            compare(mat, img);

            // convert back and compare
            try (final CvMat img2 = Utils.img2CvMat(img);) {
               assertTrue(CvRaster.pixelsIdentical(mat, img2));
            }
         }
      }
   }
}
