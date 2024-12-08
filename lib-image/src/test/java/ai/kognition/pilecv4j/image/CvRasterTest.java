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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opencv.core.CvType;
import org.opencv.imgcodecs.Imgcodecs;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.CvRaster.GetChannelValueAsInt;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.image.display.ImageDisplay.Implementation;
import ai.kognition.pilecv4j.util.DetermineShowFlag;

@SuppressWarnings("deprecation")
public class CvRasterTest extends DetermineShowFlag {

    static {
        CvMat.initOpenCv();
    }

    public static String classPathFile(final String str) {
        return new File(
            CvRasterTest.class.getClassLoader().getResource(str).getFile())
                .getAbsolutePath();
    }

    private static String testImagePath = classPathFile("test-images/expected-8bit-grey.darkToLight.bmp");

    private static int IMAGE_WIDTH_HEIGHT = 256;

    @Rule public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testMove() throws Exception {
        try(final CvMat cvmat = scopedGetAndMove();) {
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
        try(final CvMat ret = CvMat.move(Imgcodecs.imread(testImagePath, IMREAD_UNCHANGED));) {
            assertEquals(IMAGE_WIDTH_HEIGHT, ret.rows());
            assertEquals(IMAGE_WIDTH_HEIGHT, ret.cols());
            // A true std::move was applied to mat so we really shouldn't
            // access it afterward.
            return ret.returnMe();
        }
    }

    @Test
    public void testShow() throws Exception {
        if(SHOW) {
            try(final CvMat raster = ImageFile.readMatFromFile(testImagePath);
                QuietCloseable c = new ImageDisplay.Builder().implementation(Implementation.SWT)
                    .show(raster).windowName("Test").build();

                QuietCloseable c2 = new ImageDisplay.Builder().implementation(Implementation.HIGHGUI)
                    .show(raster).windowName("Test").build();

            ) {
                Thread.sleep(3000);
            }
        }
    }

    @Test
    public void testSimpleCreate() throws Exception {
        final String expectedFileLocation = testImagePath;

        try(final CvMat mat = new CvMat(IMAGE_WIDTH_HEIGHT, IMAGE_WIDTH_HEIGHT, CvType.CV_8UC1)) {
            mat.rasterAp(raster -> {
                raster.apply((BytePixelSetter)(r, c) -> new byte[] {(byte)(((r + c) >> 1) & 0xff)});

                try(final CvMat expected = Functional.uncheck(() -> ImageFile.readMatFromFile(expectedFileLocation));) {
                    expected.rasterAp(e -> assertEquals(e, raster));
                }
            });
        }
    }

    @Test
    public void testEqualsAndNotEquals() throws Exception {
        final String expectedFileLocation = testImagePath;

        try(final CvMat omat = new CvMat(IMAGE_WIDTH_HEIGHT, IMAGE_WIDTH_HEIGHT, CvType.CV_8UC1)) {
            omat.rasterAp(ra -> ra.apply((BytePixelSetter)(r, c) -> {
                if(r == 134 && c == 144)
                    return new byte[] {-1};
                return new byte[] {(byte)(((r + c) >> 1) & 0xff)};
            }));

            try(final CvMat emat = ImageFile.readMatFromFile(expectedFileLocation);) {
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
    public void testInplaceReshape() throws Exception {
        final String testImagePath = classPathFile("test-images/180x240_people.jpg");

        try(final CvMat omat = ImageFile.readMatFromFile(testImagePath);) {

            assertEquals(3, omat.channels());
            System.out.println(omat);

            try(final CvMat rmat = ImageFile.readMatFromFile(testImagePath);) {
                final long originalDataLocation = rmat.rasterOp(r -> r.getNativeAddressOfData());
                // let's make this a big ole flat matrix
                rmat.inplaceReshape(1, new int[] {(int)(omat.total() * omat.channels())});

                assertEquals(1, rmat.channels());
                // System.out.println(rmat);
                // oddly, you can't have a 1d mat
                assertEquals(2, rmat.dims());
                assertEquals(omat.total() * omat.elemSize(), rmat.rows());
                // make sure it hasn't moved
                assertEquals(originalDataLocation, rmat.rasterOp(r -> r.getNativeAddressOfData()).longValue());

                // reshape it to rows,cols,chan
                rmat.inplaceReshape(1, new int[] {omat.rows(),omat.cols(),3});
                // make sure it hasn't moved
                assertEquals(originalDataLocation, rmat.rasterOp(r -> r.getNativeAddressOfData()).longValue());
                assertEquals(omat.total() * omat.elemSize(), rmat.total() * rmat.elemSize());
                assertNotEquals(omat.total(), rmat.total());

                final int rows = omat.rows();
                final int cols = omat.cols();
                rmat.rasterAp(r -> {
                    final ByteBuffer bb = r.underlying();
                    for(int i = 0; i < rows; i++) {
                        for(int j = 0; j < cols; j++) {
                            final double[] pix = omat.get(i, j);
                            for(int c = 0; c < 3; c++) {
                                assertEquals(pix[c], 0xff & bb.get((((i * cols) + j) * 3) + c), 0);
                            }
                        }
                    }
                });
            }
        }
    }

    @Test
    public void testInplaceRemake() throws Exception {
        try(final CvMat omat = ImageFile.readMatFromFile(classPathFile("test-images/180x240_people.jpg"));) {

            assertEquals(3, omat.channels());
            System.out.println(omat);

            try(final CvMat rmat = CvMat.shallowCopy(omat);) {
                final long originalDataLocation = omat.rasterOp(r -> r.getNativeAddressOfData());
                // let's make this a big ole flat matrix
                rmat.inplaceRemake(new int[] {(int)(omat.total() * omat.channels())}, CvType.CV_8UC1, rmat.total() * rmat.elemSize());

                assertEquals(1, rmat.channels());
                // System.out.println(rmat);
                // oddly, you can't have a 1d mat
                assertEquals(2, rmat.dims());
                assertEquals(omat.total() * omat.elemSize(), rmat.rows());
                // make sure it hasn't moved
                assertEquals(originalDataLocation, rmat.rasterOp(r -> r.getNativeAddressOfData()).longValue());

                // reshape it to rows,cols,chan
                rmat.inplaceRemake(new int[] {omat.rows(),omat.cols(),3}, CvType.CV_8UC1, rmat.total() * rmat.elemSize());
                // make sure it hasn't moved
                assertEquals(originalDataLocation, rmat.rasterOp(r -> r.getNativeAddressOfData()).longValue());
                assertEquals(omat.total() * omat.elemSize(), rmat.total() * rmat.elemSize());
                assertNotEquals(omat.total(), rmat.total());

                final int rows = omat.rows();
                final int cols = omat.cols();
                rmat.rasterAp(r -> {
                    final ByteBuffer bb = r.underlying();
                    for(int i = 0; i < rows; i++) {
                        for(int j = 0; j < cols; j++) {
                            final double[] pix = omat.get(i, j);
                            for(int c = 0; c < 3; c++) {
                                assertEquals(pix[c], 0xff & bb.get((((i * cols) + j) * 3) + c), 0);
                            }
                        }
                    }
                });
            }
        }

        // 256x256 gray scale
        try(final CvMat omat = ImageFile.readMatFromFile(testImagePath);) {
            assertEquals(1, omat.channels());
            System.out.println(omat);

            try(final CvMat rmat = CvMat.shallowCopy(omat);) {

                rmat.inplaceRemake(new int[] {omat.rows(),omat.cols() >> 2}, CvType.CV_32SC1, rmat.total() * rmat.elemSize());

                final int rows = omat.rows();
                final int cols = omat.cols();

                rmat.rasterAp(r -> {
                    for(int i = 0; i < rows; i++) {
                        for(int j = 0; j < cols; j++) {
                            final double[] pix = omat.get(i, j);
                            final int val = (int)pix[0];
                            // now extract the exact same value.
                            final int rcols = j >>> 2;
                            final int index = j & 3;
                            final double[] rpixD = rmat.get(i, rcols);
                            final int rpix = (int)rpixD[0];
                            // endian-ness doesn't matter since I'm not interpreting the integer.
                            final int rmatch = ((rpix) >>> (index * 8) & 0xff);
                            assertEquals(val, rmatch);
                        }
                    }
                });
            }
        }
    }

    @Test
    public void testReduce() throws Exception {
        try(final CvMat mat = new CvMat(255, 255, CvType.CV_8UC1);) {
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
