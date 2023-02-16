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

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.EncodingContext;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.MediaContext;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestSplitIntoFiles extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFfmpeg2.class);

    @Rule public TemporaryFolder tempDir = new TemporaryFolder();

    private static long spitVideoIntoFiles(final File inputFile, final File destDir) {
        final AtomicLong frameNum = new AtomicLong(0);

        try(final MediaContext sctx = Ffmpeg.createMediaContext()
            .source(inputFile.getAbsolutePath())
            .chain("default")

            .selectFirstVideoStream()
            .processVideoFrames(f -> {
                final String filename = new File(destDir, "image-" + frameNum.getAndIncrement() + ".jpg").getAbsolutePath();
                try(CvMat toWrite = f.bgr(false);) {
                    uncheck(() -> ImageFile.writeImageFile(toWrite, filename));
                }
                assertTrue(new File(filename).exists());
            })

            .mediaContext()

        ;) {
            sctx.play();
        }
        return frameNum.get();
    }

    private static void encodeFiles(final File imageDir, final long numFrames, final File outputVideo) throws IOException {
        try(final CvMat firstFrame = ImageFile.readMatFromFile(new File(imageDir, "image-0.jpg").getAbsolutePath());

            final EncodingContext ectx = Ffmpeg.createEncoder()
                .muxer(Muxer.create(outputVideo.getAbsolutePath()))
                .videoEncoder("libx264", "vidEncoder")
                .addCodecOptions("preset", "slow")
                .addCodecOptions("crf", "40")

                .enable(firstFrame, false)

        ;) {

            final VideoEncoder ve = ectx.getExistingVideoEncoder("vidEncoder");
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
                MediaContext sc = Ffmpeg.createMediaContext()
                    .source(destination.getAbsolutePath())
                    .sync()
                    .chain("default")
                    .processVideoFrames(f -> {
                        try(CvMat d = f.bgr(false);) {
                            id.update(d);
                        }
                    })
                    .mediaContext();) {
                sc.play();
            }
        }
    }

}
