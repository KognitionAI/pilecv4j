package ai.kognition.pilecv4j.image;

import static org.junit.Assert.assertEquals;

import java.util.stream.IntStream;

import org.junit.Test;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Size;

import ai.kognition.pilecv4j.image.CvRaster.FlatFloatPixelConsumer;

public class CvMatOfPoint2fTest {
    @Test
    public void canOpenClose() {
        try(final CvMatOfPoint2f empty = new CvMatOfPoint2f();
            final CvMatOfPoint2f notEmpty = new CvMatOfPoint2f(IntStream.range(0, 3)
                .mapToObj(i -> new Point(i, i))
                .toArray(Point[]::new))) {
            assertEquals(0, empty.cols());
            assertEquals(0, empty.rows());

            assertEquals(1, notEmpty.cols());
            assertEquals(3, notEmpty.rows());
            assertEquals(2, notEmpty.channels());
        }
    }

    @Test
    public void canFlattenAndTranspose() {
        try(final CvMatOfPoint2f matOfPoint = new CvMatOfPoint2f(IntStream.range(0, 3)
            .mapToObj(i -> new Point(i, i))
            .toArray(Point[]::new));
            final var t = matOfPoint.asCvMat(false, true);
            final var f = matOfPoint.asCvMat(true, false);
            final var ft = matOfPoint.asCvMat(true, true)) {

            assertEquals(new Size(1, 3), matOfPoint.size());
            assertEquals(new Size(3, 1), t.size());
            assertEquals(new Size(2, 3), f.size());
            assertEquals(new Size(3, 2), ft.size());
        }
    }

    @Test
    public void canMakeToAndFromCvMat() {
        final int numPoints = 10;
        try(final CvMatOfPoint2f matOfPoint = new CvMatOfPoint2f(IntStream.range(0, numPoints)
            .mapToDouble(i -> i)
            .mapToObj(i -> new Point(i, i))
            .toArray(Point[]::new));
            final CvMat regularOlMat = matOfPoint.asCvMat(true, false);
            final CvMat reshaped = CvMat.move(regularOlMat.reshape(2, numPoints));
            final CvMatOfPoint2f matOfPointAgain = new CvMatOfPoint2f(reshaped);
            final CvMat diff = new CvMat();) {
            Core.subtract(matOfPoint, matOfPointAgain, diff);
            diff.rasterOp(r -> (FlatFloatPixelConsumer)(i, pix) -> {
                for(final float val: pix) {
                    assertEquals(0f, val, 10e-4f);
                }
            });
        }
    }
}
