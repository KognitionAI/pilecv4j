package ai.kognition.pilecv4j.image;

import static ai.kognition.pilecv4j.image.UtilsForTesting.findAll;
import static ai.kognition.pilecv4j.image.UtilsForTesting.translateClasspath;
import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.opencv.imgcodecs.Imgcodecs;

import net.dempsy.util.Functional;

import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestImageFile {
    public final static boolean SHOW = CvRasterTest.SHOW;

    static {
        CvMat.initOpenCv();
    }

    @Test
    public void testDecodeImageData() {
        final String testImg = translateClasspath("test-images/types");
        final List<File> allFiles =

            Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs)).stream()
                .collect(Collectors.toList());

        try(var id = SHOW ? new ImageDisplay.Builder().build() : null;) {
            assertTrue(10 < allFiles.stream()
                .map(fn -> fn.getAbsolutePath())
                // make sure imread can read the file already.
                .filter(fn -> {
                    try(CvMat m = CvMat.move(Imgcodecs.imread(fn));) {
                        return m != null && m.rows() > 0;
                    }
                })
                // conver the file to a byte array
                .map(fn -> {
                    return uncheck(() -> {
                        try(InputStream is = new FileInputStream(new File(fn));) {
                            return IOUtils.toByteArray(is);
                        }
                    });
                })

                // ==============================================
                // This is what we're testing
                // ==============================================
                .map(bb -> ImageFile.decodeImageData(bb))
                // ==============================================

                .peek(m -> assertNotNull(m))
                .peek(m -> Optional.ofNullable(id).ifPresent(d -> d.update(m)))
                .peek(m -> m.close())
                .count());
        }
    }

    @Test
    public void testEncodeDecodeImageData() {
        final String testImg = translateClasspath("test-images/types");
        final List<File> allFiles =

            Functional.chain(new ArrayList<File>(), fs -> findAll(new File(testImg), fs)).stream()
                .collect(Collectors.toList());

        try(var id = SHOW ? new ImageDisplay.Builder().build() : null;) {
            assertTrue(10 < allFiles.stream()
                .map(fn -> fn.getAbsolutePath())
                .map(fn -> Pair.of(fn, CvMat.move(Imgcodecs.imread(fn))))
                .filter(p -> p.getRight() != null)
                .filter(p -> p.getRight().rows() > 0)
                // ==============================================
                // This is what we're testing
                // ==============================================
                .map(p -> Pair.of(p.getRight(), ImageFile.encodeToImageData(p.getRight(), FilenameUtils.getExtension(p.getLeft()))))
                // ==============================================

                // ==============================================
                // This is what we're testing also
                // ==============================================
                .map(p -> Pair.of(p.getLeft(), ImageFile.decodeImageData(p.getRight())))
                // ==============================================

                .peek(p -> assertNotNull(p.getRight()))

                .peek(p -> assertEquals(p.getLeft().rows(), p.getRight().rows()))
                .peek(p -> assertEquals(p.getLeft().cols(), p.getRight().cols()))

                .peek(p -> p.getRight().close())
                .peek(p -> p.getLeft().close())
                .count());
        }
    }
}
