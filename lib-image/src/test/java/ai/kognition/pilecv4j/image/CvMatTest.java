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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.opencv.core.CvType.CV_32FC1;
import static org.opencv.core.CvType.CV_8UC1;

import java.util.stream.IntStream;

import org.junit.Test;
import org.opencv.core.Scalar;

import ai.kognition.pilecv4j.image.CvRaster.FloatPixelConsumer;

public class CvMatTest {
    private static final double EPSILON = 10e-8;

    @Test
    public void ones() {
        final int rows = 3;
        final int cols = 4;
        try(final CvMat ones = CvMat.ones(rows, cols, CV_8UC1)) {
            assertEquals(rows, ones.rows());
            assertEquals(cols, ones.cols());
            assertEquals(1, ones.channels());

            IntStream.range(0, rows)
                .forEach(row -> IntStream.range(0, cols)
                    .forEach(col -> assertArrayEquals(new double[] {1.0d}, ones.get(row, col), EPSILON)));
        }
    }

    @Test
    public void identity() {
        final int rows = 3;
        final int cols = 4;
        try(final CvMat identity = CvMat.identity(rows, cols, CV_32FC1, new Scalar(1f))) {
            assertEquals(rows, identity.rows());
            assertEquals(cols, identity.cols());
            assertEquals(1, identity.channels());
            identity.rasterOp(cvRaster -> (FloatPixelConsumer)(row, col, values) -> {
                assertEquals(1, values.length);
                if(row == col) {
                    assertEquals(1f, values[0], 1e-4);
                } else {
                    assertEquals(0f, values[0], 1e-4);
                }
            });
        }
    }
}
