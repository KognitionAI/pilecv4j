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

package ai.kognition.pilecv4j.ffmpeg;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.EncodingContext;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.MediaContext;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestFfmpegStreamingDocker extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFfmpegStreamingDocker.class);
    final int numFrames = SHOW ? 1000 : 10;

    @Rule public GenericContainer<?> docker = new GenericContainer<>(DockerImageName.parse("pilecv4j/nginx-rtmp:latest"))
        .withExposedPorts(8080, 1935);

    @Rule public final TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void testRemuxStreaming() throws Exception {
        final int rtmpPort = docker.getMappedPort(1935);

        Thread.sleep(100);

        try(final MediaContext c = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;) {

            final AtomicBoolean checkerFailed = new AtomicBoolean(false);
            final AtomicBoolean finishedSuccessfully = new AtomicBoolean(false);
            final CountDownLatch checkerLatch = new CountDownLatch(1);

            final Thread checker = startChecker(id, rtmpPort, numFrames, checkerLatch, () -> c.stop(), finishedSuccessfully, checkerFailed);

            c
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .peek(sc -> checkerLatch.countDown())
                .chain("default")
                .remux(Muxer.create("flv", "rtmp://localhost:" + rtmpPort + "/live/feedly-id"))
                .mediaContext()
                .sync()
                .play();

            assertTrue(poll(o -> !checker.isAlive()));
            assertFalse(checkerFailed.get());
            assertTrue(finishedSuccessfully.get());

        }
    }

    @Test
    public void testEncodingStream() throws Exception {
        final int rtmpPort = docker.getMappedPort(1935);
        Thread.sleep(100);

        try(
            final EncodingContext encoder = Ffmpeg.createEncoder();
            final MediaContext ctx = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            final AtomicBoolean checkerFailed = new AtomicBoolean(false);
            final AtomicBoolean finishedSuccessfully = new AtomicBoolean(false);
            final CountDownLatch checkerLatch = new CountDownLatch(1);
            final AtomicBoolean triggerWhenDone = new AtomicBoolean(false);

            final Thread checker = startChecker(id, rtmpPort, numFrames, checkerLatch, () -> triggerWhenDone.set(true), finishedSuccessfully, checkerFailed);

            encoder
                .muxer(Muxer.create("flv", "rtmp://localhost:" + rtmpPort + "/live/feedly-id"))
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
                .source(STREAM)
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num / details[0].fps_den);

                })
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
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
                .mediaContext()
                .sync()
                .play()

            ;

            assertTrue(poll(o -> !checker.isAlive()));
            assertFalse(checkerFailed.get());
            assertTrue(finishedSuccessfully.get());
        }

    }

    private static Thread startChecker(final ImageDisplay id, final int rtmpPort, final int numFrames, final CountDownLatch checkerLatch,
        final Runnable triggerWhenDone, final AtomicBoolean finishedSuccessfully, final AtomicBoolean checkerFailed) {
        final Thread checker = new Thread(() -> {
            try {
                uncheck(() -> checkerLatch.await());

                final AtomicInteger framecount = new AtomicInteger(0);

                ignore(() -> Thread.sleep(1000));

                try(final MediaContext sc = Ffmpeg.createMediaContext();) {

                    sc
                        .addOption("flags", "low_delay")
                        .addOption("fflags", "nobuffer")
                        .source("rtmp://localhost:" + rtmpPort + "/live/feedly-id")
                        .chain("default")
                        .selectFirstVideoStream()
                        .processVideoFrames(m -> {
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
                        .mediaContext()
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
