package ai.kognition.pilecv4j.image;

import static ai.kognition.pilecv4j.image.UtilsForTesting.compare;
import static ai.kognition.pilecv4j.image.UtilsForTesting.translateClasspath;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.Implementation;

import static org.junit.Assert.assertEquals;

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
    public void test() {
        final int x = 65535 * 65535;
        System.out.println((int)((x & 0xffff0000) * (1.0 / 65536.0)) & 0xffff);
        System.out.println((x >> 16) & 0xffff);

        System.out.println(-1 >>> (8 + 16));
        System.out.println(~((1 << (8 + 8)) - 1));
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
            assertEquals(3, mat.channels());
            compare(mat, im);
        }
    }

    @Test
    public void testAgain() throws Exception {
        final String testImg = translateClasspath("test-images/types");
        final List<File> allFiles = Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs));

        allFiles.forEach(imgFile -> {
            final BufferedImage img = Functional.uncheck(() -> ImageFile.readBufferedImageFromFile(imgFile.getAbsolutePath()));
            System.out.println(img.getColorModel().getColorSpace().isCS_sRGB());

            System.out.println(img);
            System.out.println(img.getColorModel().getClass().getSimpleName());
            System.out.println(img.getColorModel());
        });
    }

    @Test
    public void testConversions() throws Exception {
        ImageFile.readerClassPrefix = "com.github.jaiimageio.";
        final String testImg = translateClasspath("test-images/types");

        try (final ImageDisplay id = SHOW ? new ImageDisplay.Builder().implementation(Implementation.SWT).build() : null;) {
            final List<File> allFiles = Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs));
            allFiles.stream()
                    .forEach(imageFile -> {
                        // if(checkConvert(imageFile, id))
                        // checkConvert(imageFile.getAbsolutePath(), id);
                        checkConvert(imageFile, id);
                    });
        }
    }

    private static void findAll(final File file, final List<File> files) {
        if(file.isDirectory())
            Arrays.stream(file.list()).forEach(f -> findAll(new File(file, f), files));
        else
            files.add(file);
    }

    // private static void findAll(final File file, final List<File> files) {
    // if(file.isDirectory())
    // Arrays.stream(file.list()).forEach(f -> {
    // final File nf = new File(file, f);
    // if(nf.isFile())
    // files.add(new File(file, f));
    // });
    // else
    // files.add(file);
    // }

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

    private static boolean checkConvert(final File imgFile, final ImageDisplay id) {
        System.out.println(imgFile);
        final BufferedImage img = Functional.uncheck(() -> ImageFile.readBufferedImageFromFile(imgFile.getAbsolutePath()));

        try (CvMat mat = Utils.img2CvMat(img);) {
            System.out.println(mat);

            if(id != null) {
                id.update(mat);
                Functional.uncheck(() -> Thread.sleep(500));
            }

            // System.out.println("Running forward compare on :" + imgFile);

            compare(mat, img);

            // convert back and compare
            // TODO: don't skip this comparison ever
            if(mat.depth() == CvType.CV_8U || mat.depth() == CvType.CV_8S) {
                final BufferedImage img2 = Utils.mat2Img(mat);
                // System.out.println("Running inverse compare on :" + imgFile);
                compare(img, img2);
                return false;
            }

            return true;
        }
    }

    // private static void checkConvert(final String imgFile, final ImageDisplay id) {
    // try (final CvMat mat = Functional.uncheck(() -> ImageFile.readMatFromFile(imgFile));) {
    //
    // if(mat == null) // we couldn't read it yet.
    // return;
    //
    // if(id != null) {
    // id.update(mat);
    // Functional.uncheck(() -> Thread.sleep(500));
    // }
    //
    // // can't convert mat2Img (a)RGB images with components larger than 8-bits.
    // if(((mat.channels() == 1 && mat.depth() != CvType.CV_32F && mat.depth() != CvType.CV_64F)
    // || mat.depth() == CvType.CV_8U
    // || mat.depth() == CvType.CV_8S)
    // && mat.channels() != 2) {
    // final BufferedImage img = Utils.mat2Img(mat);
    // compare(mat, img);
    //
    // // convert back and compare
    // try (final CvMat img2 = Utils.img2CvMat(img);) {
    // assertTrue(CvRaster.pixelsIdentical(mat, img2));
    // }
    // }
    // }
    // }
}
