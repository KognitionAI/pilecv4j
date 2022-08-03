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

import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.opencv.core.CvType;

public class UtilsForTesting {

    public static final Map<Integer, Integer> biTypeToPixDelta = new HashMap<>();

    static {
        biTypeToPixDelta.put(TYPE_USHORT_555_RGB, 1);
        biTypeToPixDelta.put(TYPE_USHORT_565_RGB, 1);
    }

    public static String translateClasspath(final String classpathPath) {
        return new File(TestUtils.class.getClassLoader().getResource(classpathPath).getFile()).getAbsolutePath();
    }

    private static int divideRound(final int num, final int shift, final int[] lookup) {
        final long ret = (num & 0xffffffffL) >>> shift;
        return lookup[(ret > 255 ? 255 : (int)ret)];
    }

    public static void compare(final CvMat mat, final BufferedImage im) {
        compare(mat, im, -1);
    }

    public static void compare(final CvMat mat, final BufferedImage im, final int tol) {
        final boolean hasAlpha = im.getColorModel().hasAlpha();

        final int[] lookup = new int[256];
        for(int i = 0; i < 256; i++)
            lookup[i] = i;
        if(mat instanceof CvMatWithColorInformation) {
            final CvMatWithColorInformation matWc = (CvMatWithColorInformation)mat;
            if(matWc.isGray && matWc.iCC) {
                // getLinearRGB8TosRGB8LUT(lookup);
                final ColorSpace cs = im.getColorModel().getColorSpace();
                if(cs instanceof ICC_ColorSpace) {
                    final double minCs = cs.getMinValue(0);
                    final double maxCs = cs.getMaxValue(0);
                    for(int i = 0; i < 255; i++) {
                        final double val = ((i / 255.0D) * (maxCs - minCs)) + minCs;
                        final var result = cs.toRGB(new float[] {(float)val});
                        final double luValNorm = result[result.length - 1];
                        lookup[i] = (int)((luValNorm * 255.0) + 0.5);
                    }
                }
            }
        }

        final Integer pixDeltaInt = biTypeToPixDelta.get(im.getType());
        final int pixDelta = tol != -1 ? tol
            : Math.max(2, Math.min(2, pixDeltaInt == null ? IntStream.range(0, lookup.length - 1)
                .map(i -> lookup[i + 1] - lookup[i])
                .max()
                .getAsInt()
                : pixDeltaInt.intValue()));

        final int channels = mat.channels();
        assertTrue(hasAlpha ? channels > 3 || channels == 2 : true);

        // check that the number of bands equals
        final boolean checkRgbIsGray;

        // These wont always match if the ColorSpace is custom
        if(im.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_CMYK) {
            // this is a workaround for a bug in TwelveMokey's DiscreteAlphaIndexColorModel
            // see: https://github.com/haraldk/TwelveMonkeys/issues/693
            final int numComponents;
            {
                final int tmp1 = im.getColorModel().getNumComponents();
                final int tmp2 = im.getColorModel().getNumColorComponents() + (hasAlpha ? 1 : 0);
                numComponents = Math.max(tmp1, tmp2);
            }
            // in some cases greyscale is expanded into RGB
            if(numComponents == 1 && channels == 3)
                checkRgbIsGray = true;
            else {
                checkRgbIsGray = false;
                assertEquals(numComponents, channels);
            }
        } else
            checkRgbIsGray = false;

        mat.rasterAp(raster -> {
            final Function<Object, int[]> pixelToInt = CvRaster.pixelToIntsConverter(raster.type());

            for(int r = 0; r < mat.rows(); r++) {
                for(int c = 0; c < mat.cols(); c++) {

                    // if(r == 0 && c == 1)
                    // System.out.println();

                    final int[] rgb = new int[3];
                    int alpha = -1;

                    {
                        final Color biPixel;
                        biPixel = new Color(im.getRGB(c, r), hasAlpha);

                        if(biPixel != null) {
                            rgb[0] = biPixel.getRed();
                            rgb[1] = biPixel.getGreen();
                            rgb[2] = biPixel.getBlue();
                            if(hasAlpha)
                                alpha = biPixel.getAlpha();
                        }
                    }

                    // int[] bpp = im.getColorModel().getComponentSize();
                    // int[] shift = new int[bpp.length];
                    // for(int ch = 0; ch < bpp.length; ch++)
                    // shift[ch] = bpp[ch] <= 8 ? ((mat.depth() == CvType.CV_16U) ? 8 : 0) : 0;
                    final int shift;
                    if(mat.depth() == CvType.CV_8U || mat.depth() == CvType.CV_8S)
                        shift = 0;
                    else if(mat.depth() == CvType.CV_16U || mat.depth() == CvType.CV_16S)
                        shift = 8;
                    else if(mat.depth() == CvType.CV_32S)
                        shift = (im.getColorModel().getComponentSize(0) - 8);
                    else
                        // throw new IllegalArgumentException();
                        shift = 0;

                    final int[] matPixel = pixelToInt.apply(raster.get(r, c));
                    // System.out.println(Arrays.toString(matPixel));
                    final String msg = "" + Arrays.toString(rgb) + " != " + Arrays.toString(matPixel) + " at [" + r + "," + c
                        + "] with a shift:" + shift;
                    if(channels == 1 || (channels == 2 && hasAlpha)) {
                        assertEquals(msg + " grey", rgb[0], divideRound(matPixel[0], shift, lookup), pixDelta);
                        if(channels == 2)
                            assertEquals(msg + " alpha", alpha, divideRound(matPixel[1], shift, lookup), pixDelta);
                        assertEquals(rgb[0], rgb[1]);
                        assertEquals(rgb[0], rgb[2]);
                    } else {
                        assertEquals(msg + " red", rgb[0], divideRound(matPixel[2], shift, lookup), pixDelta);
                        assertEquals(msg + " green", rgb[1], divideRound(matPixel[1], shift, lookup), pixDelta);
                        assertEquals(msg + " blue", rgb[2], divideRound(matPixel[0], shift, lookup), pixDelta);
                        if(channels > 3 && hasAlpha)
                            assertEquals(msg + " alpha", alpha, divideRound(matPixel[3], shift, lookup), pixDelta);
                        if(checkRgbIsGray) {
                            assertEquals(rgb[0], rgb[1]);
                            assertEquals(rgb[0], rgb[2]);
                        }
                    }
                }
            }
        });
    }

    public static void compare(final BufferedImage biA, final BufferedImage biB) {
        if(biTypeToPixDelta.containsKey(biA.getType()) || biTypeToPixDelta.containsKey(biB.getType()))
            return; // can't check if we expect a delta.

        // take buffer data from botm image files //
        final int w = biA.getWidth();
        final int h = biA.getHeight();
        assertEquals(w, biB.getWidth());
        assertEquals(h, biB.getHeight());

        final int[] pixelsA = biA.getRGB(0, 0, w, h, null, 0, w);
        final int[] pixelsB = biB.getRGB(0, 0, w, h, null, 0, w);

        assertEquals(pixelsA.length, pixelsB.length);
        assertEquals(w * h, pixelsA.length);

        // ignore the LSb of each
        IntStream.range(0, pixelsA.length).forEach(i -> pixelsA[i] = pixelsA[i] & 0xfefefefe);
        IntStream.range(0, pixelsB.length).forEach(i -> pixelsB[i] = pixelsB[i] & 0xfefefefe);

        assertArrayEquals(pixelsA, pixelsB);
    }

}
