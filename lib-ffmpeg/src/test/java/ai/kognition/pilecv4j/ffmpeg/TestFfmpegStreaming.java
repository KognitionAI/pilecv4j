package ai.kognition.pilecv4j.ffmpeg;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ShutdownStrategy;
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.StreamContext;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestFfmpegStreaming extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFfmpegStreaming.class);
    final int numFrames = SHOW ? 1000 : 10;

    @Rule public final TestRule docker = DockerComposeRule.builder()
        .file(uncheck(() -> new Vfs().toFile(new URI("classpath:///pilecv4j-rtmp-docker-compose.yml"))).getAbsolutePath())
        // .waitingForHostNetworkedPort(8080, p -> p.isHttpRespondingSuccessfully(port -> "http://localhost:" + port + "/", false))
        .waitingForHostNetworkedPort(8080, p -> p.isListeningNow() ? SuccessOrFailure.success() : SuccessOrFailure.failure("not yet"))
        .removeConflictingContainersOnStartup(true)
        .shutdownStrategy(ShutdownStrategy.KILL_DOWN)
        .build();

    @Rule public final TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testRemuxStreaming() throws Exception {
        try(final StreamContext c = Ffmpeg2.createStreamContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;) {

            final AtomicBoolean checkerFailed = new AtomicBoolean(false);
            final AtomicBoolean finishedSuccessfully = new AtomicBoolean(false);
            final CountDownLatch checkerLatch = new CountDownLatch(1);

            final Thread checker = startChecker(id, numFrames, checkerLatch, () -> c.stop(), finishedSuccessfully, checkerFailed);

            c
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                .peek(sc -> sc.load())
                .peek(sc -> checkerLatch.countDown())
                .openChain()
                .createUriRemuxer("flv", "rtmp://localhost:1935/live/feedly-id")
                .streamContext()
                .sync()
                .play();

            assertTrue(poll(o -> !checker.isAlive()));
            assertFalse(checkerFailed.get());
            assertTrue(finishedSuccessfully.get());

        }
    }

    @Test
    public void testEncodingStream() throws Exception {
        try(
            final EncodingContext encoder = Ffmpeg2.createEncoder();
            final StreamContext ctx = Ffmpeg2.createStreamContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            final AtomicBoolean checkerFailed = new AtomicBoolean(false);
            final AtomicBoolean finishedSuccessfully = new AtomicBoolean(false);
            final CountDownLatch checkerLatch = new CountDownLatch(1);
            final AtomicBoolean triggerWhenDone = new AtomicBoolean(false);

            final Thread checker = startChecker(id, numFrames, checkerLatch, () -> triggerWhenDone.set(true), finishedSuccessfully, checkerFailed);

            encoder
                .outputStream("flv", "rtmp://localhost:1935/live/feedly-id")
                .openVideoEncoder("libx264", "default")
                .addCodecOptions("profile", "baseline")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("tune", "zerolatency")
                .addCodecOptions("threads", "1")
                .addCodecOptions("g", "12")

            ;

            final VideoEncoder ve1 = encoder.getVideoEncoder("default");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .createMediaDataSource(STREAM)
                .load()
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num / details[0].fps_den);

                })
                .openChain()
                .createFirstVideoStreamSelector()
                .createVideoFrameProcessor(f -> {
                    if(firstFrame.get()) {
                        firstFrame.set(false);
                        ve1.enable(f, f.isRgb);
                        encoder.ready();
                        checkerLatch.countDown();
                    }
                    ve1.encode(f, f.isRgb);
                    if(triggerWhenDone.get()) {
                        ve1.encode(f, f.isRgb); // push the frame through a few more time to make sure the checker shuts down
                        ve1.encode(f, f.isRgb); // push the frame through a few more time to make sure the checker shuts down
                        ve1.encode(f, f.isRgb); // push the frame through a few more time to make sure the checker shuts down

                        ctx.stop();
                    }
                })
                .streamContext()
                .sync()
                .play()

            ;

            assertTrue(poll(o -> !checker.isAlive()));
            assertFalse(checkerFailed.get());
            assertTrue(finishedSuccessfully.get());
        }

    }

    private static Thread startChecker(final ImageDisplay id, final int numFrames, final CountDownLatch checkerLatch, final Runnable triggerWhenDone,
        final AtomicBoolean finishedSuccessfully, final AtomicBoolean checkerFailed) {
        final Thread checker = new Thread(() -> {
            try {
                uncheck(() -> checkerLatch.await());

                final AtomicInteger framecount = new AtomicInteger(0);

                ignore(() -> Thread.sleep(1000));

                try(final StreamContext sc = Ffmpeg2.createStreamContext();) {

                    sc
                        .addOption("flags", "low_delay")
                        .addOption("fflags", "nobuffer")
                        .createMediaDataSource("rtmp://localhost:1935/live/feedly-id")
                        .openChain()
                        .createFirstVideoStreamSelector()
                        .createVideoFrameProcessor(m -> {
                            if(id != null) {
                                try(CvMat mat = m.bgr(false);) {
                                    id.update(mat);
                                }
                            }
                            framecount.getAndIncrement();
                            if(framecount.get() >= numFrames) {
                                finishedSuccessfully.set(true);
                                System.out.println("Finishing checker");
                                triggerWhenDone.run();
                                sc.stop();
                            }
                        })
                        .streamContext()
                        .play();
                }
            } catch(final Exception e) {
                LOGGER.error("FAILED IN CHECKER THREAD:", e);
                checkerFailed.set(true);
            }
        }, "Checker-Thread");
        checker.start();
        return checker;
    }

}
