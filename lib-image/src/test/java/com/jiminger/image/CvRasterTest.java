package com.jiminger.image;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.jiminger.image.CvRaster.BytePixelSetter;

public class CvRasterTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    static {
        System.loadLibrary("opencv_java340");
        System.loadLibrary("-image-native.jiminger.com");
    }

    @Test
    public void test() throws Exception {
        final String expectedFileLocation = new File(
                getClass().getClassLoader().getResource("test-images/expected-8bit-grey.darkToLight.bmp").getFile())
                        .getPath();
        final Mat mat = ImageFile.readMatFromFile(expectedFileLocation);
        final CvRaster underTest = CvRaster.manage(mat);
        underTest.show();
        Thread.sleep(20000);
    }

    @Test
    public void testSimpleCreate() throws Exception {
        final String expectedFileLocation = new File(
                getClass().getClassLoader().getResource("test-images/expected-8bit-grey.darkToLight.bmp").getFile())
                        .getPath();

        try (final CvRaster raster = CvRaster.createManaged(256, 256, CvType.CV_8UC1)) {
            raster.apply((BytePixelSetter) (r, c) -> new byte[] { (byte) (((r + c) >> 1) & 0xff) });

            final CvRaster expected = CvRaster.manage(ImageFile.readMatFromFile(expectedFileLocation));
            assertEquals(expected, raster);
        }
    }

    @Test
    public void testEqualsAndNotEquals() throws Exception {
        final String expectedFileLocation = new File(
                getClass().getClassLoader().getResource("test-images/expected-8bit-grey.darkToLight.bmp").getFile())
                        .getPath();

        try (final CvRaster raster = CvRaster.createManaged(256, 256, CvType.CV_8UC1)) {
            raster.apply((BytePixelSetter) (r, c) -> {
                if (r == 134 && c == 144)
                    return new byte[] { -1 };
                return new byte[] { (byte) (((r + c) >> 1) & 0xff) };
            });

            final CvRaster expected = CvRaster.manage(ImageFile.readMatFromFile(expectedFileLocation));
            assertNotEquals(expected, raster);

            // correct the pixel
            raster.set(134, 144, new byte[] { (byte) (((134 + 144) >> 1) & 0xff) });
            assertEquals(expected, raster);
        }
    }
}
