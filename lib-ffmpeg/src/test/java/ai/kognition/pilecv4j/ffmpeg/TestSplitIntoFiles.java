package ai.kognition.pilecv4j.ffmpeg;

import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.StreamContext;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestSplitIntoFiles extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFfmpeg2.class);

    @Rule public TemporaryFolder tempDir = new TemporaryFolder();

    private static long spitVideoIntoFiles(final File inputFile, final File destDir) {
        final AtomicLong frameNum = new AtomicLong(0);

        try(final StreamContext sctx = Ffmpeg2.createStreamContext()
            .createMediaDataSource(inputFile.getAbsolutePath())
            .openChain("default")

            .createFirstVideoStreamSelector()
            .createVideoFrameProcessor(f -> {
                final String filename = new File(destDir, "image-" + frameNum.getAndIncrement() + ".jpg").getAbsolutePath();
                try(CvMat toWrite = f.bgr(false);) {
                    uncheck(() -> ImageFile.writeImageFile(toWrite, filename));
                }
                assertTrue(new File(filename).exists());
            })

            .streamContext()

        ;) {
            sctx.play();
        }
        return frameNum.get();
    }

    private static void encodeFiles(final File imageDir, final long numFrames, final File outputVideo) throws IOException {
        try(final CvMat firstFrame = ImageFile.readMatFromFile(new File(imageDir, "image-0.jpg").getAbsolutePath());

            final EncodingContext ectx = Ffmpeg2.createEncoder()
                .outputStream(outputVideo.getAbsolutePath())
                .openVideoEncoder("libx264", "vidEncoder")
                .addCodecOptions("preset", "slow")
                .addCodecOptions("crf", "40")
                // .setFps(30)

                .enable(firstFrame, false)

        ;) {

            final VideoEncoder ve = ectx.getVideoEncoder("vidEncoder");
            ectx.ready();

            LongStream.range(0, numFrames)
                .mapToObj(fn -> new File(imageDir, "image-" + fn + ".jpg").getAbsolutePath())
                .forEach(frameFile -> {
                    try(CvMat mat = uncheck(() -> ImageFile.readMatFromFile(frameFile));) {
                        ve.encode(mat, false);
                    }
                });
        }

    }

    @Test
    public void testSplitFrames() throws Exception {
        LOGGER.info("Running test: {}.testSplitFrames()", TestSplitIntoFiles.class.getSimpleName());

        final File workingFolder = tempDir.newFolder();

        final long numFrames = spitVideoIntoFiles(STREAM_FILE, workingFolder);
        final File destination = tempDir.newFile("out.mp4");
        // final File destination = new File("/tmp/out.mp4");
        encodeFiles(workingFolder, numFrames, destination);

        assertTrue(destination.exists());
        System.out.println("Input file size :" + STREAM_FILE.length());
        System.out.println("Output file size:" + destination.length());

        if(SHOW) {
            try(ImageDisplay id = new ImageDisplay.Builder().build();
                StreamContext sc = Ffmpeg2.createStreamContext()
                    .createMediaDataSource(destination.getAbsolutePath())
                    .sync()
                    .openChain("default")
                    .createVideoFrameProcessor(f -> {
                        try(CvMat d = f.bgr(false);) {
                            id.update(d);
                        }
                    })
                    .streamContext();) {
                sc.play();
            }
        }
    }

}
