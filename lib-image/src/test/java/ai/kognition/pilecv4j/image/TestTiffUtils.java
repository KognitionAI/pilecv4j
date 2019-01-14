package ai.kognition.pilecv4j.image;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.Test;

import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.Implementation;
import ai.kognition.pilecv4j.image.tiff.TiffUtils;

import mil.nga.tiff.TIFFImage;
import mil.nga.tiff.TiffReader;

public class TestTiffUtils {

   @Test
   public void testDumpMeta() throws IOException {
      final File file = new File("C:\\Users\\Jim\\Pictures\\2001 Fl Trip - Universal.HDRi 001.tif");
      final TIFFImage image = TiffReader.readTiff(file);
      TiffUtils.dumpMeta(image, System.out);
   }

   @Test
   public void test2() throws Exception {
      final File file = new File("C:\\Users\\Jim\\Pictures\\2001 Fl Trip - Universal.HDRi 001.tif");

      final BufferedImage bi = ImageFile.readBufferedImageFromFile(file.getAbsolutePath(), 1);

      try (CvMat mat = Utils.img2CvMat(bi);
            ImageDisplay id = new ImageDisplay.Builder().implementation(Implementation.SWT).show(mat).build();) {
         Thread.sleep(10000);
      }
   }
}
