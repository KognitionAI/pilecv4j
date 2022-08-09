/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.image;

import static ai.kognition.pilecv4j.image.UtilsForTesting.compare;
import static ai.kognition.pilecv4j.image.UtilsForTesting.findAll;
import static ai.kognition.pilecv4j.image.UtilsForTesting.translateClasspath;
import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import net.dempsy.util.Functional;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.Implementation;

public class TestUtils {
    // private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);
    final String testImageFilename = translateClasspath("test-images/people.jpeg");
    final static long SLEEP_TIME = 50;

    public final static boolean SHOW = CvRasterTest.SHOW;
    // private final static boolean SHOW = true;

    @Test
    public void testMatToImg() throws Exception {
        try(CvMat mat = ImageFile.readMatFromFile(testImageFilename);) {
            final BufferedImage im = Utils.mat2Img(mat);
            if(SHOW) {
                try(final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                    final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            compare(mat, im);
        }
    }

    @Ignore // fails due to color space (maybe ICC profile)
    @Test
    public void testMatToImgGray() throws Exception {
        try(CvMat omat = ImageFile.readMatFromFile(testImageFilename);
            CvMat mat = Operations.convertToGray(omat);) {
            final BufferedImage im = Utils.mat2Img(mat);
            Imgproc.cvtColor(omat, mat, Imgproc.COLOR_BGR2GRAY);
            if(SHOW) {
                try(final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                    final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            compare(mat, im, 4);
        }
    }

    @Test
    public void testFakeMatToImg() throws Exception {
        try(CvMat mat = new CvMat(255, 255, CvType.CV_8UC3);) {
            mat.rasterAp(raster -> {
                raster.apply((BytePixelSetter)(r, c) -> new byte[] {0,0,(byte)c});
            });
            final BufferedImage im = Utils.mat2Img(mat);

            if(SHOW) {
                try(final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                    final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            compare(mat, im);
        }
    }

    @Test
    public void testImgToMat() throws Exception {
        final BufferedImage im = ImageFile.readBufferedImageFromFile(testImageFilename);
        try(final CvMat mat = Utils.img2CvMat(im);) {
            if(SHOW) {
                try(final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                    final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            assertEquals(3, mat.channels());
            compare(mat, im);
        }
    }

    final public static Set<String> notWorkingTestAgain = new HashSet<>(Arrays.asList(

    ));

    @Test
    public void testAgain() throws Exception {
        final String testImg = translateClasspath("test-images/types");
        final List<File> allFiles =

            Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs)).stream()
                .filter(f -> !notWorkingTestAgain.stream()
                    .filter(nwfn -> f.getAbsolutePath().endsWith(nwfn))
                    .findAny()
                    .isPresent())
                .collect(Collectors.toList());

        // List
        // .of(new File("/data/jim/kog/code/third-party/pilecv4j/lib-image/target/test-classes/test-images/types/TYPE_0/img58.tif"));

        try(final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;) {
            allFiles.stream()
                .sorted()
                .forEach(imgFile -> {
                    // System.out.println(imgFile);
                    final BufferedImage img = Functional.uncheck(() -> ImageFile.readBufferedImageFromFile(imgFile.getAbsolutePath()));
                    // System.out.println(img.getColorModel().getColorSpace().isCS_sRGB());

                    if(id != null) {
                        try(var mat = Utils.img2CvMat(img);) {
                            try(CvMat toDisplay = mat.displayable();) {
                                id.update(toDisplay);
                            }
                        }
                        uncheck(() -> Thread.sleep(SLEEP_TIME));
                        System.gc();
                        System.gc();
                        System.gc();
                    }
                });
        }
    }

    final public static Set<String> notWorkingTestConversion = new HashSet<>(Arrays.asList(
    // "TYPE_10/img04.jpg"

    ));

    @Test
    public void testConversions() throws Exception {
        final String testImg = translateClasspath("test-images/types");
        final List<File> allFiles =

            Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs)).stream()
                .filter(f -> !notWorkingTestConversion.stream()
                    .filter(nwfn -> f.getAbsolutePath().endsWith(nwfn))
                    .findAny()
                    .isPresent())
                .sorted((fl, fr) -> fl.getAbsolutePath().compareTo(fr.getAbsolutePath()))
                .collect(Collectors.toList());

        // List
        // .of(new File("/data/jim/kog/code/third-party/pilecv4j/lib-image/target/test-classes/test-images/types/TYPE_0/img34.tif"));

        try(final ImageDisplay id = SHOW ? new ImageDisplay.Builder().implementation(Implementation.SWT).build() : null;) {
            allFiles.stream()
                .forEach(imageFile -> {
                    checkConvert(imageFile, id);
                    System.gc();
                    System.gc();
                    System.gc();
                });
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

        try(final CvMat mat = Utils.img2CvMat(im);) {
            if(SHOW) {
                try(final ImageDisplay id = new ImageDisplay.Builder().show(mat).implementation(Implementation.HIGHGUI).build();
                    final ImageDisplay id2 = new ImageDisplay.Builder().show(mat).implementation(Implementation.SWT).build();) {
                    Thread.sleep(SLEEP_TIME);
                }
            }
            assertEquals(1, mat.channels());
            compare(mat, im);
        }
    }

    @Test
    public void testScaleUp() throws Exception {
        final String file = translateClasspath("test-images/180x240_people.jpg");
        final int resizeH = 360; // Requires scale up by 2
        final int resizeW = 500; // Requires scale up by 2.08
        try(CvMat mat = ImageFile.readMatFromFile(file);) {
            final Size toResizeTo = Utils.scaleWhilePreservingAspectRatio(mat, new Size(resizeW, resizeH));
            assertEquals("Scaled width up by a factor of 2 (lower factor)", 480, toResizeTo.width, 1E-8);
            assertEquals("Scaled height up by a factor of 2 (lower factor)", resizeH, toResizeTo.height, 1E-8);
        }
    }

    @Test
    public void testScaleDown() throws Exception {
        final String file = translateClasspath("test-images/180x240_people.jpg");
        final int resizeH = 100; // Requires scale down by a factor of 0.5555
        final int resizeW = 120; // Requires scale down by a factor of 0.5
        try(CvMat mat = ImageFile.readMatFromFile(file);) {
            final Size toResizeTo = Utils.scaleWhilePreservingAspectRatio(mat, new Size(resizeW, resizeH));
            assertEquals("Scaled width down by a factor of 0.5 (lower factor)", resizeW, toResizeTo.width, 1E-8);
            assertEquals("Scaled height down by a factor of 0.5 (lower factor)", 90, toResizeTo.height, 1E-8);
        }
    }

    @Test
    public void testMixedScale() throws Exception {
        final String file = translateClasspath("test-images/180x240_people.jpg");
        final int resizeH = 360; // Requires scale up by a factor of 2
        final int resizeW = 80; // Requires scale down by a factor of 0.333 .
        try(CvMat mat = ImageFile.readMatFromFile(file);) {
            final Size toResizeTo = Utils.scaleWhilePreservingAspectRatio(mat, new Size(resizeW, resizeH));
            assertEquals("Scaled width down by a factor of 0.333 (scale down is prioritized)", resizeW, toResizeTo.width, 1E-8);
            assertEquals("Scaled height down by a factor of 0.333 (scale down is prioritized)", 60, toResizeTo.height, 1E-8);
        }
    }

    private static void checkConvert(final File imgFile, final ImageDisplay id) {
        System.out.println(imgFile);
        final BufferedImage img = Functional.uncheck(() -> ImageFile.readBufferedImageFromFile(imgFile.getAbsolutePath()));

        try(var mat = Utils.img2CvMat(img);) {
            System.out.println(mat);

            if(id != null) {
                try(CvMat toDisplay = mat.displayable();) {
                    id.update(toDisplay);
                }
                Functional.uncheck(() -> Thread.sleep(SLEEP_TIME));
            }

            System.out.println("Running forward compare on :" + imgFile);

            compare(mat, img);

            // convert back and compare
            // TODO: don't skip this comparison ever
            // if(mat.depth() == CvType.CV_8U || mat.depth() == CvType.CV_8S) {
            // final BufferedImage img2 = Utils.mat2Img(mat);
            // // System.out.println("Running inverse compare on :" + imgFile);
            // compare(img, img2);
            // }
        }
    }
}
