package ai.kognition.pilecv4j.image;

import static ai.kognition.pilecv4j.image.UtilsForTesting.compare;
import static ai.kognition.pilecv4j.image.UtilsForTesting.translateClasspath;
import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_CUSTOM;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
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
        TYPE_INT_ARGB, // 2 - No samples load to this format
        TYPE_INT_ARGB_PRE, // 3 - No samples load to this format
        TYPE_INT_BGR, // 4 - No samples load to this format
        TYPE_3BYTE_BGR, // 5
        TYPE_4BYTE_ABGR, // 6
        TYPE_4BYTE_ABGR_PRE, // 7 - No samples load to this format
        TYPE_USHORT_565_RGB, // 8
        TYPE_USHORT_555_RGB, // 9
        TYPE_BYTE_GRAY, // 10
        TYPE_USHORT_GRAY // 11
        // 12, TYPE_BYTE_BINARY - Can't handle this yet.
        // 13, TYPE_BYTE_INDEXED - Can't handle this yet.
    }));

    final public static Set<String> notWorking = new HashSet<>(Arrays.asList(
    // "TYPE_0/img08.bmp"
    // , "TYPE_0/img21.pcx"
    // , "TYPE_0/img20.pcx"
    // , "TYPE_0/img34.tif"
    // , "TYPE_0/img18.pcx"
    // , "TYPE_0/img45.tif"
    // , "TYPE_0/img44.tif"
    // , "TYPE_0/img16.pcx"
    // , "TYPE_0/img22.PCX"
    // , "TYPE_0/img45.tif"
    // , "TYPE_0/img46.tif"
    // , "TYPE_0/img47.tif"
    // // These seem to be a problem for non TwelveMonkeys IIO plugins
    // , "TYPE_0/img13.tif"
    // , "TYPE_0/img56.tif"
    // , "TYPE_0/img11.tif"
    // , "TYPE_0/img53.tif"
    // , "TYPE_0/img91.tif"
    // , "TYPE_0/img12.tif"
    // , "TYPE_0/img55.tif"
    // , "TYPE_0/img32.tif"
    // , "TYPE_0/img54.tif"

    ));

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
        final String testImg = translateClasspath("test-images/types");

        try (final ImageDisplay id = SHOW ? new ImageDisplay.Builder().implementation(Implementation.SWT).build() : null;) {
            final List<File> allFiles = Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs));
            System.out.println(allFiles);
            allFiles.stream()
                .filter(f -> !notWorking.stream()
                    .filter(toExclude -> f.toURI().toString().endsWith(toExclude))
                    .findAny()
                    .isPresent())
                .forEach(imageFile -> {
                    // if(checkConvert(imageFile, id))
                    // checkConvert(imageFile.getAbsolutePath(), id);
                    checkConvert(imageFile, id);
                });
        }
    }

    // private static void findAll(final File file, final List<File> files) {
    // if(file.isDirectory())
    // Arrays.stream(file.list()).forEach(f -> findAll(new File(file, f), files));
    // else
    // files.add(file);
    // }

    private static void findAll(final File file, final List<File> files) {
        if(file.isDirectory())
            Arrays.stream(file.list()).forEach(f -> {
                final File nf = new File(file, f);
                if(nf.isFile())
                    files.add(new File(file, f));
            });
        else
            files.add(file);
    }

    private static boolean checkConvert(final File imgFile, final ImageDisplay id) {
        final BufferedImage img = Functional.uncheck(() -> ImageFile.readBufferedImageFromFile(imgFile.getAbsolutePath()));

        // // TODO: this list needs to be empty
        // if(!typesWereTesting.contains(img.getType()))
        // return false;

        // TODO: Don't skip IndexColorModel
        // if(!(img.getColorModel() instanceof DirectColorModel))
        // return false;

        // // TODO: Don't skip TYPE_CUSTOM with alpha.
        // if(img.getColorModel().getNumComponents() == 4 && img.getType() ==
        // TYPE_CUSTOM)
        // return false;

        // // TODO: Don't skip images with greater than 16 bits per channel data.
        // if(img.getType() == TYPE_CUSTOM &&
        // Arrays.stream(img.getColorModel().getComponentSize())
        // .filter(cs -> cs > 16)
        // .findAny()
        // .isPresent() && true)
        // return false;

        // TODO: Handle TYPE_CUSTOM with more than one bank in the data buffer
        // if(img.getType() == TYPE_CUSTOM &&
        // img.getData().getDataBuffer().getNumBanks() != 1)
        // return false;

        // TODO: handle custom images that aren't 3 channel color
        // if(img.getType() == TYPE_CUSTOM && img.getColorModel().getNumComponents() !=
        // 3)
        // return false;

        try (CvMat mat = Utils.img2CvMat(img);) {

            if(id != null) {
                id.update(mat);
                Functional.uncheck(() -> Thread.sleep(500));
            }

            System.out.println("Running forward compare on :" + imgFile);

            compare(mat, img);

            // convert back and compare
            // TODO: don't skip this comparison ever
            if(mat.depth() == CvType.CV_8U || mat.depth() == CvType.CV_8S) {
                final BufferedImage img2 = Utils.mat2Img(mat);
                System.out.println("Running inverse compare on :" + imgFile);
                compare(img, img2);
                return false;
            }

            return true;
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
