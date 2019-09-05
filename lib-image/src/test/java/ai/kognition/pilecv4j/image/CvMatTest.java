package ai.kognition.pilecv4j.image;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.opencv.core.CvType.CV_8UC1;

import java.util.stream.IntStream;

import org.junit.Test;

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
}
