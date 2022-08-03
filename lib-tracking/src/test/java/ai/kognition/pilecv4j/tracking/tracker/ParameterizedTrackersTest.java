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

package ai.kognition.pilecv4j.tracking.tracker;

import static ai.kognition.pilecv4j.image.geometry.Point.ocv;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.opencv.core.CvType.CV_8UC3;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.tracking.Tracker;
import ai.kognition.pilecv4j.tracking.TrackerImpl;

@RunWith(Parameterized.class)
public class ParameterizedTrackersTest {
    private static boolean isShowSet() {
        final String sysOpSHOW = System.getProperty("pilecv4j.SHOW");
        final boolean sysOpSet = sysOpSHOW != null;
        boolean show = ("".equals(sysOpSHOW) || Boolean.parseBoolean(sysOpSHOW));
        if(!sysOpSet)
            show = Boolean.parseBoolean(System.getenv("PILECV4J_SHOW"));
        return show;
    }

    private final TrackerImpl trackerImpl;
    private final double maxPixelDistance;

    public ParameterizedTrackersTest(final TrackerImpl trackerImpl, final double maxPixelDistance) {
        this.trackerImpl = trackerImpl;
        this.maxPixelDistance = maxPixelDistance;
    }

    @Parameters(name = "{index}={0}")
    public static Collection<Object[]> params() {
        return List.of(new Object[] {TrackerImpl.CSRT,5d}, new Object[] {TrackerImpl.KCF,7.5d}, new Object[] {TrackerImpl.MOSSE,10d});
    }

    @Test
    public void canNativeCreateAndDelete() throws Exception {
        try(final Tracker toBeDeleted = trackerImpl.get()) {
            try(final Tracker ignored = toBeDeleted) {
                ignored.skipOnceForReturn();
            }
        }
    }

    @Test
    public void canInitialize() throws Exception {
        try(var vfs = new Vfs();
            final CvMat lenna = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///lenna.png")).getAbsolutePath());
            final Tracker tracker = trackerImpl.get();) {

            final Rect2d initialBbox = new Rect2d(new Point(182, 176), new Point(380, 406));

            assertTrue(tracker.initialize(lenna, initialBbox)
                .isPresent());
            assertTrue(tracker.isInitialized());
            assertTrue(tracker.update(lenna)
                .isPresent());
        }
    }

    @Test
    public void canTrackCircle() throws Exception {
        assumeFalse("MOSSE tracker is unable to extract features and track from the drawn feature.", trackerImpl == TrackerImpl.MOSSE);

        // Make a circle that moves in a circle and track it.
        final int rows = 1080;
        final int cols = 1920;
        final int center_rows = cols / 2;
        final int center_cols = rows / 2;
        final int radiusOfMovement = 200;
        final int radiusOfCircle = 50;
        final int maxIterations = 250;

        final Point textLoc = new Point(cols - 20, 20);

        final boolean enableDisplay = isShowSet();

        final Function<Integer, Double> getRowPos = iteration -> {
            final double radians = 2.0 * Math.PI * iteration / maxIterations;
            return radiusOfMovement * Math.sin(radians) + center_rows;
        };
        final Function<Integer, Double> getColPos = iteration -> {
            final double radians = 2.0 * Math.PI * iteration / maxIterations;
            return radiusOfMovement * Math.cos(radians) + center_cols;
        };

        try(final Tracker tracker = trackerImpl.get();
            final ImageDisplay display = enableDisplay ? new ImageDisplay.Builder().build() : null) {

            // Initialize the tracker and display if necessary
            try(final CvMat initialMat = CvMat.zeros(rows, cols, CV_8UC3)) {
                final Point initialCentroid = new Point(getRowPos.apply(0), getColPos.apply(0));
                drawTrackedShape(initialMat, initialCentroid, radiusOfCircle);
                final Rect2d initialBoundingBox = rectFromCentroid(initialCentroid, radiusOfCircle + 1);
                assertTrue(tracker.initialize(initialMat, initialBoundingBox)
                    .isPresent());
                assertTrue(tracker.isInitialized());
                if(enableDisplay) {
                    Imgproc.putText(initialMat, "0", textLoc, 0, 15, new Scalar(0, 255, 0));
                    Imgproc.rectangle(initialMat, new Rect(initialBoundingBox.tl(), initialBoundingBox.br()), new Scalar(0, 255, 0), 2);
                    display.update(initialMat);
                }
            }

            final List<Object[]> results = IntStream.range(0, maxIterations)
                .mapToObj(i -> {
                    try(final CvMat matNumberI = CvMat.zeros(rows, cols, CV_8UC3)) {
                        final Point centroid = new Point(getRowPos.apply(i), getColPos.apply(i));
                        drawTrackedShape(matNumberI, centroid, radiusOfCircle);
                        final Rect2d expectedBbox = rectFromCentroid(centroid, radiusOfCircle + 1);

                        final Optional<Rect2d> update = tracker.update(matNumberI);

                        if(enableDisplay) {
                            Imgproc.putText(matNumberI, Integer.toString(i), textLoc, 0, 15, new Scalar(0, 255, 0));
                            Imgproc.rectangle(matNumberI, new Rect(expectedBbox.tl(), expectedBbox.br()), new Scalar(0, 255, 0), 2);
                            update.ifPresent(trackBox -> {
                                Imgproc.rectangle(matNumberI, new Rect(trackBox.tl(), trackBox.br()), new Scalar(0, 0, 255), 2);
                                Imgproc.line(matNumberI, centroid, centroidFromRect(trackBox), new Scalar(0, 0, 255), 3);
                            });
                            display.update(matNumberI);
                        }
                        return new Object[] {expectedBbox,update};
                    }
                })
                .collect(Collectors.toList());

            IntStream.range(0, maxIterations)
                .forEach(i -> {
                    final Rect2d expected = (Rect2d)results.get(i)[0];
                    @SuppressWarnings("unchecked")
                    final Optional<Rect2d> update = (Optional<Rect2d>)results.get(i)[1];
                    assertTrue(i + ": tracker should have seen the circle.", update.isPresent());
                    final Point centroid = centroidFromRect(expected);
                    final Point trackedCentroid = centroidFromRect(update.get());
                    final double distanceBetweenExpectedAndActual = ocv(centroid).distance(ocv(trackedCentroid));
                    assertTrue(i + ": tracked centroid should be within a " + maxPixelDistance + " pixel distance of the expected centroid but was instead " +
                        distanceBetweenExpectedAndActual + " pixels away", distanceBetweenExpectedAndActual <= maxPixelDistance);
                    assertTrue(i + ": tracked bounding box's height should be roughly the same size as the expected bounding box's dimensions.",
                        Math.abs(update.get().height - (2 * radiusOfCircle)) <= maxPixelDistance);
                    assertTrue(i + ": tracked bounding box's width should be roughly the same size as the expected bounding box's dimensions.",
                        Math.abs(update.get().width - (2 * radiusOfCircle)) <= maxPixelDistance);
                });
        }
    }

    private static void drawTrackedShape(final Mat mat, final Point centroid, final int radius) {
        Imgproc.circle(mat, centroid, radius, new Scalar(255, 255, 255), -1);
    }

    private static Rect2d rectFromCentroid(final Point centroid, final int radius) {
        return new Rect2d(new Point(centroid.x - radius, centroid.y - radius), new Size(radius * 2, radius * 2));
    }

    private static Point centroidFromRect(final Rect2d rect) {
        return new Point((rect.width * 0.5) + rect.x, (rect.height * 0.5) + rect.y);
    }
}
