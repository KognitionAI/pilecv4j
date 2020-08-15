package ai.kognition.pilecv4j.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opencv.imgcodecs.Imgcodecs.IMREAD_COLOR;
import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opencv.core.MatOfRect;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.CvRaster;
import ai.kognition.pilecv4j.image.ImageFile;

public class CvCascadeClassifierTest {
    @Rule public final ExpectedException expected = ExpectedException.none();

    @Test
    public void runsAndCloses() throws IOException {
        final ClassLoader cl = CvCascadeClassifierTest.class.getClassLoader();

        try(final CvMat mat = ImageFile.readMatFromFile(cl.getResource("test.bmp")
            .toString(), IMREAD_COLOR);
            final CvMat grayscaleMat = new CvMat();
            final CvRaster.Closer closer = new CvRaster.Closer();
            final CvCascadeClassifier classifier = new CvCascadeClassifier();) {
            classifier.loadModel(new File(cl.getResource("haarcascade_frontalface_default.xml")
                .getFile()));

            assertTrue("Should be able to load model.", classifier.hasModelLoaded());

            Imgproc.cvtColor(mat, grayscaleMat, COLOR_BGR2GRAY);

            final MatOfRect rects = closer.addMat(new MatOfRect());
            classifier.detectMultiScale(mat, rects);

            assertEquals(1, rects.toList()
                .size());
        }
    }

    @Test
    public void cantDoubleLoadModel() {
        final ClassLoader cl = CvCascadeClassifierTest.class.getClassLoader();
        expected.expect(IllegalStateException.class);

        try(final CvCascadeClassifier classifier = new CvCascadeClassifier()) {
            classifier.loadModel(new File(cl.getResource("haarcascade_frontalface_default.xml")
                .getFile()));
            classifier.loadModel(new File(cl.getResource("haarcascade_frontalface_default.xml")
                .getFile()));
        }
    }
}
