package ai.kognition.pilecv4j.image.geometry;

import static ai.kognition.maths.Maths.EPSILON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.opencv.core.Core.BORDER_DEFAULT;
import static org.opencv.core.Core.BORDER_REPLICATE;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.IntStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.geometry.transform.GaussianBlur;

@RunWith(Parameterized.class)
public class GaussianBlurTest {

    private final URL imageToTransform;
    private final URL expectedImageResult;
    private final Size size;
    private final double sigmaX;
    private final double sigmaY;
    private final int borderType;

    @Parameterized.Parameters
    public static Collection parameters() {
        return Arrays.asList(new Object[][] {{GaussianBlurTest.class.getClassLoader().getResource("test-images/flower.jpg"),
                                              GaussianBlurTest.class.getClassLoader().getResource("test-images/flower_3x3ExpectedOutput.bmp"), new Size(3, 3),
                                              0, 0, BORDER_DEFAULT}, {GaussianBlurTest.class.getClassLoader().getResource("test-images/flower.jpg"),
                                                                      GaussianBlurTest.class.getClassLoader().getResource(
                                                                          "test-images/flower_7x7ExpectedOutput.bmp"), new Size(7, 7), 1, 0, BORDER_DEFAULT},
                                             {GaussianBlurTest.class.getClassLoader().getResource("test-images/flower.jpg"),
                                              GaussianBlurTest.class.getClassLoader().getResource("test-images/flower_5x3ExpectedOutput.bmp"), new Size(5, 3),
                                              1, 0.5, BORDER_DEFAULT}, {GaussianBlurTest.class.getClassLoader().getResource("test-images/flower.jpg"),
                                                                        GaussianBlurTest.class.getClassLoader().getResource(
                                                                            "test-images/flower_borderTestExpectedOutput.bmp"), new Size(5, 3), 0, 0,
                                                                        BORDER_REPLICATE},
                                             {GaussianBlurTest.class.getClassLoader().getResource("test-images/fruit.jpg"),
                                              GaussianBlurTest.class.getClassLoader().getResource("test-images/fruit_3x3ExpectedOutput.bmp"), new Size(3, 3), 0,
                                              0, BORDER_DEFAULT}, {GaussianBlurTest.class.getClassLoader().getResource("test-images/fruit.jpg"),
                                                                   GaussianBlurTest.class.getClassLoader().getResource(
                                                                       "test-images/fruit_7x7ExpectedOutput.bmp"), new Size(7, 7), 1, 0, BORDER_DEFAULT},
                                             {GaussianBlurTest.class.getClassLoader().getResource("test-images/fruit.jpg"),
                                              GaussianBlurTest.class.getClassLoader().getResource("test-images/fruit_5x3ExpectedOutput.bmp"), new Size(5, 3), 1,
                                              0.5, BORDER_DEFAULT}, {GaussianBlurTest.class.getClassLoader().getResource("test-images/fruit.jpg"),
                                                                     GaussianBlurTest.class.getClassLoader().getResource(
                                                                         "test-images/fruit_borderTestExpectedOutput.bmp"), new Size(5, 3), 0, 0,
                                                                     BORDER_REPLICATE}});
    }

    public GaussianBlurTest(final URL imageToTransform, final URL expectedImageResult, final Size size, final double sigmaX, final double sigmaY,
        final int borderType) throws IOException {
        this.imageToTransform = imageToTransform;
        this.expectedImageResult = expectedImageResult;
        this.size = size;
        this.sigmaX = sigmaX;
        this.sigmaY = sigmaY;
        this.borderType = borderType;
    }

    @Test
    public void canBlur() throws IOException {
        System.out.println(imageToTransform.getFile() + " " + size.width + " " + size.height);
        try(final CvMat matToTransform = ImageFile.readMatFromFile(imageToTransform.getFile());
            final CvMat matExpectedResult = ImageFile.readMatFromFile(expectedImageResult.getFile())) {
            assertNotNull(matToTransform);
            assertNotNull(matExpectedResult);

            final GaussianBlur transform = new GaussianBlur(size, sigmaX, sigmaY, borderType);
            final Mat transformedMat = transform.gaussianBlur(matToTransform);
            assertNotNull(transformedMat);

            assertEquals(matExpectedResult.rows(), transformedMat.rows());
            assertEquals(matExpectedResult.cols(), transformedMat.cols());
            assertEquals(matExpectedResult.channels(), transformedMat.channels());

            IntStream.range(0, matExpectedResult.rows())
                .forEach(row -> {
                    IntStream.range(0, matExpectedResult.cols())
                        .forEach(col -> {
                            IntStream.range(0, matExpectedResult.channels())
                                .forEach(channel -> {
                                    assertEquals(matExpectedResult.get(row, col)[channel], transformedMat.get(row, col)[channel], EPSILON);
                                });
                        });
                });
        }
    }
}
