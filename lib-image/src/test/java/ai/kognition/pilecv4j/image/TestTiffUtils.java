package ai.kognition.pilecv4j.image;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

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

}
