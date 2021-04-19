package ai.kognition.pilecv4j.ffmpeg;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.StreamContext;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestFfmpeg extends BaseTest {

    // public static String TEST_VIDEO = "file:///tmp/test-videos/living-room-tracking.mp4";
    // public static String TEST_VIDEO = "file:///tmp/test-videos/Libertas-70sec.mp4";
    // public static String TEST_VIDEO = "rtsp://admin:gregormendel1@172.16.2.11:554/";

    @Test
    public void testInit() {
        FfmpegApi.ffmpeg_init();
    }

    @Test
    public void testCreateContext() {
        try(StreamContext c = Ffmpeg.createStreamContext();) {

        }
    }

    @Test
    public void testOpenStream() {
        try(StreamContext c = Ffmpeg.createStreamContext();) {
            c.openStream(STREAM);
        }
    }

    @Test
    public void testConsumeFrames() {
        final AtomicLong frameCount = new AtomicLong(0);
        try(final StreamContext c = Ffmpeg.createStreamContext();
            final ImageDisplay id = new ImageDisplay.Builder().build()) {
            c.openStream(STREAM);
            c.processFrames(f -> {
                frameCount.getAndIncrement();
                if(SHOW) {
                    try(final CvMat rgb = f.bgr(false);) {
                        id.update(rgb);
                    }
                }
            });
        }

        assertTrue(frameCount.get() > 50);
    }

}
