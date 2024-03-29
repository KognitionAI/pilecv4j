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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.MutableInt;
import net.dempsy.util.MutableRef;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.EncodingContext;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.MediaContext;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

@RunWith(Parameterized.class)
public class TestFfmpeg2 extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFfmpeg2.class);
    public static final int MEG = 1024 * 1024;

    @Rule public TemporaryFolder tempDir = new TemporaryFolder();

    private final boolean sync;

    public TestFfmpeg2(final boolean sync) {
        this.sync = sync;
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            {false},
            {true},
        });
    }

    @Test
    public void testCreateContext() {
        LOGGER.info("Running test: {}.testCreateContext(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        try(MediaContext c = Ffmpeg.createMediaContext();) {}
    }

    @Test(expected = FfmpegException.class)
    public void testPlayNoSource() {
        LOGGER.info("Running test: {}.testPlayNoSource(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        try(MediaContext c = Ffmpeg.createMediaContext();) {
            c
                .optionally(sync, s -> s.sync())
                .play();
        }
    }

    @Test
    public void testPlayWithStop() {
        LOGGER.info("Running test: {}.testPlayWithStop(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final AtomicLong frameCount = new AtomicLong(0);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        try(final MediaContext c = Ffmpeg.createMediaContext();) {
            c
                .source(STREAM)
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
                    if(frameCount.getAndIncrement() > 50L) {
                        LOGGER.debug("Stopping the stream.");
                        c.stop();
                        stopped.set(true);
                    }
                })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        LOGGER.debug("Num frames: {}", frameCount);
        assertTrue(stopped.get());
        assertTrue(frameCount.get() >= 50L && frameCount.get() <= 60L);
    }

    @Test
    public void testConsumeFrames() {
        LOGGER.info("Running test: {}.testConsumeFrames(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final AtomicLong frameCount = new AtomicLong(0);
        final MutableRef<Ffmpeg.MediaContext.StreamDetails[]> details = new MutableRef<>(null);
//        final Throttle throttle = new Throttle(10, TimingType.FPS, LOGGER);
        try(final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;
            final MediaContext ctx = Ffmpeg.createMediaContext();) {
            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .peek(s -> {
                    details.ref = s.getStreamDetails();
                    System.out.println(Arrays.toString(details.ref));
                })
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(150,

                // "hevc_cuvid",
//                    "h264_cuvid",

                    f -> {
                        frameCount.getAndIncrement();
//                        if(throttle.include(f.decodeTimeMillis)) {
                        if(SHOW) {
                            try(final CvMat rgb = f.bgr(false);) {
                                id.update(rgb);
                            }
                        }
//                        }
                    })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        FfmpegApi.pcv4j_ffmpeg2_timings();

        assertTrue(frameCount.get() > 50);
        assertNotNull(details.ref);
        assertEquals(2, details.ref.length);
        assertEquals(Ffmpeg.AVMEDIA_TYPE_VIDEO, details.ref[0].mediaType);
        assertEquals(Ffmpeg.AVMEDIA_TYPE_AUDIO, details.ref[1].mediaType);
    }

    @Test
    public void testCustomDataSource() throws Exception {
        LOGGER.info("Running test: {}.testCustomDataSource(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final AtomicLong frameCount = new AtomicLong(0);

        // load the entire file into memory.
        final byte[] contents = FileUtils.readFileToByteArray(STREAM_FILE);
        final MutableInt pos = new MutableInt(0); // track the current position in the stream

        try(

            final MediaContext mediaContext = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;) {

            mediaContext.source(

                // read bytes from buffer
                (bb, numBytes) -> {
                    if(pos.val >= contents.length)
                        return Ffmpeg.AVERROR_EOF_AVSTAT;
                    final int size = bb.capacity();
                    LOGGER.trace("buf size: {}, to write: {}, from pos: {}", size, numBytes, pos.val);
                    final int numToSend = (numBytes + (int)pos.val > contents.length) ? (contents.length - (int)pos.val) : numBytes;
                    LOGGER.trace("contents read from {} to {}({})", pos.val, (pos.val + numToSend), numToSend);
                    bb.put(contents, (int)pos.val, numToSend);
                    pos.val += numToSend;
                    if(pos.val >= contents.length)
                        LOGGER.debug("Last bytes being sent!");
                    return numToSend;
                },

                // need to support seek to read some mp4 files
                (final long offset, final int whence) -> {
                    if(whence == Ffmpeg.SEEK_SET)
                        pos.val = offset;
                    else if(whence == Ffmpeg.SEEK_CUR)
                        pos.val += offset;
                    else if(whence == Ffmpeg.SEEK_END)
                        pos.val = contents.length - offset;
                    else if(whence == Ffmpeg.AVSEEK_SIZE)
                        return contents.length;
                    else
                        return -1;
                    return pos.val;
                })
                .chain("default")
                .selectFirstVideoStream()

                // test the double open
                .mediaContext()
                .chain("default")

                .processVideoFrames(f -> {
                    frameCount.getAndIncrement();
                    if(SHOW) {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    }
                })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play();

            assertTrue(frameCount.get() > 50);
        }
    }

    @Test
    public void testRemux() throws Exception {
        LOGGER.info("Running test: {}.testRemux(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.flv");
        if(destination.exists())
            destination.delete();
        try(

            final MediaContext c = Ffmpeg.createMediaContext();) {
            c
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .chain("default")
                .filterPackets((mediaType, stream_index, packetNumBytes, isKeyFrame, pts, dts, tbNum, tbDen) -> {
                    // System.out.println(isKeyFrame ? "keyframe" : "not keyframe");
                    return true;
                })
                .remux(Muxer.create(destination.getAbsolutePath()))
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play();
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
    }

    @Ignore
    @Test
    public void testDumpTiming() throws Exception {
        LOGGER.info("Running test: {}.testDumpTiming(sync={})", TestFfmpeg2.class.getSimpleName(), sync);

        try(final MediaContext c = Ffmpeg.createMediaContext("/tmp/out_00.ts")
            .filterPackets(p -> {
                System.out.println("pts: " + p.pts() + ", dts: " + p.dts() + " time_base: [ " + p.tbNum() + " / " + p.tbDen() + " ]");
                return true;
            })
            .play();

        ) {

        }
    }

    // @Ignore
    @Test
    public void testSegmentedRemux() throws Exception {
        LOGGER.info("Running test: {}.testSegmentedRemux(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out");
        if(destination.exists())
            destination.delete();
        // we're going to
        try(final MediaContext c = Ffmpeg.createMediaContext();) {

            c
                .source(STREAM)
                // .source("rtsp://admin:gregormendel1@172.16.2.11:554/")
                .addOption("flags", "+cgop")
                .chain("default")
//                .selectFirstVideoStream()
                .remux(Muxer.create(index -> {
                    System.out.println("Muxer #" + index);
                    return Muxer.create("mpegts", String.format("%s_%02d.%s", destination.getAbsolutePath(), index, "ts"));
                }, new PacketFilter() {

                    private long frameCount = 0;

                    @Override
                    public boolean test(final int mediaType, final int stream_index, final int packetNumBytes, final boolean isKeyFrame, final long pts,
                        final long dts, final int tbNum, final int tbDen) {
                        frameCount++;
                        if(frameCount > (5 * 30)) {
                            frameCount = 0;
                            return true;
                        }
                        return false;
                    }
                }))
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play();
        }

        final File dir = destination.getParentFile();
        assertTrue(dir.exists());
        final List<File> tsFiles = Arrays.stream(dir.listFiles())
            .filter(f -> f.getAbsolutePath().endsWith(".ts"))
            .sorted((o1, o2) -> o1.getAbsolutePath().compareTo(o2.getAbsolutePath()))
            .collect(Collectors.toList());

        assertTrue(tsFiles.size() > 1);
        final File destFile = new File(dir, "out.ts");
        try(var os = new BufferedOutputStream(new FileOutputStream(destFile));) {
            for(final File f: tsFiles) {
                try(var is = new BufferedInputStream(new FileInputStream(f));) {
                    IOUtils.copy(is, os);
                }
            }
        }

        assertTrue(destFile.exists());
        assertTrue(destFile.isFile());
        assertTrue(destFile.length() > 0);

        assertTrue(frameCount(destFile.toURI()) > 1000);
    }

    @Test
    public void testCustomRemux() throws Exception {
        LOGGER.info("Running test: {}.testCustomRemux(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.ts");
        if(destination.exists())
            destination.delete();
        try(

            final MediaContext c = Ffmpeg.createMediaContext();
            final OutputStream os = new BufferedOutputStream(new FileOutputStream(destination));

        ) {
            c
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .chain("default")
                .remux(Muxer.create("mpegts", (packet, numBytes) -> {
                    packet.rewind();
                    final byte[] pkt = new byte[numBytes];
                    packet.get(pkt);
                    uncheck(() -> os.write(pkt));
                }))
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play();
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
    }

    @Test
    public void testCustomRemuxWithSeek() throws Exception {
        LOGGER.info("Running test: {}.testCustomRemuxWithSeek(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.mp4");
        if(destination.exists())
            destination.delete();

        final var bb = ByteBuffer.allocate(20 * 1024 * 1024);
        final AtomicBoolean calledSeek = new AtomicBoolean(false);

        try(

            final MediaContext c = Ffmpeg.createMediaContext();

        ) {

            c
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .chain("default")
                .remux(Muxer.create("mp4", (packet, numBytes) -> {
                    packet.rewind();
                    final byte[] pkt = new byte[numBytes];
                    packet.get(pkt);
                    bb.put(pkt);
                },
                    (MediaDataSeek)(final long offset, final int whence) -> {
                        calledSeek.set(true);
                        if(whence == Ffmpeg.SEEK_SET)
                            bb.position((int)offset);
                        else if(whence == Ffmpeg.SEEK_CUR)
                            bb.position(bb.position() + (int)offset);
                        else if(whence == Ffmpeg.SEEK_END)
                            bb.position(bb.limit() - (int)offset);
                        else if(whence == Ffmpeg.AVSEEK_SIZE)
                            return bb.limit();
                        else
                            return -1;
                        return bb.position();
                    }))
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play();

        }

        bb.flip();
        try(final var fos = new FileOutputStream(destination);
            final FileChannel fc = fos.getChannel();) {
            fc.write(bb);
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
        assertTrue(calledSeek.get());
    }

    @Test
    public void testEncodingSimple() throws Exception {
        LOGGER.info("Running test: {}.testEncodingSimple(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.mp4");
        // final File destination = new File("/tmp/out.mp4");
        if(destination.exists())
            destination.delete();

        try(
            final MediaContext ctx = Ffmpeg.createMediaContext(STREAM);
            // final MediaContext ctx = Ffmpeg.createMediaContext("rtsp://admin:gregormendel1@172.16.2.11:554/");
            final EncodingContext encoder = Ffmpeg.createEncoder(destination.getAbsolutePath())
                .setFps(ctx)
                .setOutputDims(900, 200, true, true);
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .selectFirstVideoStream()
                .processVideoFrames(f -> encoder.encode(f))
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
    }

    @Test
    public void testEncoding() throws Exception {
        LOGGER.info("Running test: {}.testEncoding(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.mp4");
        if(destination.exists())
            destination.delete();

        try(
            final EncodingContext encoder = Ffmpeg.createEncoder();
            final MediaContext ctx = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .muxer(Muxer.create(destination.getAbsolutePath()))
                .videoEncoder("libx265", "first")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .addCodecOptions("flags", "low_delay")
                .encodingContext()
                .videoEncoder("libx265", "second")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .addCodecOptions("flags", "low_delay")
                .encodingContext()

            ;

            final VideoEncoder ve1 = encoder.getExistingVideoEncoder("first");
            final VideoEncoder ve2 = encoder.getExistingVideoEncoder("second");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num, details[0].fps_den);
                    ve2.setFps(details[0].fps_num, details[0].fps_den);

                })
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
                    if(firstFrame.get()) {
                        firstFrame.set(false);
                        ve1.enable(f, f.isRgb);
                        try(CvMat flipped = new CvMat();) {
                            Core.flip(f, flipped, 1);
                            ve2.enable(flipped, f.isRgb);
                        }
                        encoder.ready();
                    }
                    ve1.encode(f, f.isRgb);
                    try(CvMat flipped = new CvMat();) {
                        Core.flip(f, flipped, 1);
                        ve2.encode(flipped, f.isRgb);
                    }
                    if(id != null) {
                        try(var bgr = f.bgr(false);) {
                            id.update(bgr);
                        }
                    }
                })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
    }

    @Test
    public void testEncodingCustomOutput() throws Exception {
        LOGGER.info("Running test: {}.testEncodingCustomOutput(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.ts");
        if(destination.exists())
            destination.delete();

        try(
            final OutputStream os = new BufferedOutputStream(new FileOutputStream(destination));
            final EncodingContext encoder = Ffmpeg.createEncoder();
            final MediaContext ctx = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .muxer(Muxer.create("mpegts", (packet, numBytes) -> {
                    packet.rewind();
                    final byte[] pkt = new byte[numBytes];
                    packet.get(pkt);
                    uncheck(() -> os.write(pkt));
                }))
                .videoEncoder("libx265", "first")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .addCodecOptions("flags", "low_delay")
                .setTargetBitrate(2 * MEG)
                .encodingContext()
                .videoEncoder("libx265", "second")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .addCodecOptions("flags", "low_delay")
                .setTargetBitrate(2 * MEG)
                .encodingContext()

            ;

            final VideoEncoder ve1 = encoder.getExistingVideoEncoder("first");
            final VideoEncoder ve2 = encoder.getExistingVideoEncoder("second");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num, details[0].fps_den);
                    ve2.setFps(details[0].fps_num, details[0].fps_den);

                })
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
                    if(firstFrame.get()) {
                        firstFrame.set(false);
                        ve1.enable(f, f.isRgb);
                        try(CvMat flipped = new CvMat();) {
                            Core.flip(f, flipped, 1);
                            ve2.enable(flipped, f.isRgb);
                        }
                        encoder.ready();
                    }
                    ve1.encode(f, f.isRgb);
                    try(CvMat flipped = new CvMat();) {
                        Core.flip(f, flipped, 1);
                        ve2.encode(flipped, f.isRgb);
                    }
                    if(id != null) {
                        try(var bgr = f.bgr(false);) {
                            id.update(bgr);
                        }
                    }
                })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
    }

    @Test
    public void testEncodingCustomOutputWithSeek() throws Exception {
        LOGGER.info("Running test: {}.testEncodingCustomOutputWithSeek(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final File destination = tempDir.newFile("out.mp4");
        if(destination.exists())
            destination.delete();

        final var bb = ByteBuffer.allocate(100 * 1024 * 1024);
        final AtomicBoolean calledSeek = new AtomicBoolean(false);

        try(
            final EncodingContext encoder = Ffmpeg.createEncoder();
            final MediaContext ctx = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .muxer(Muxer.create("mp4", (packet, numBytes) -> {
                    packet.rewind();
                    final byte[] pkt = new byte[numBytes];
                    packet.get(pkt);
                    bb.put(pkt);
                },
                    (MediaDataSeek)(final long offset, final int whence) -> {
                        calledSeek.set(true);
                        if(whence == Ffmpeg.SEEK_SET)
                            bb.position((int)offset);
                        else if(whence == Ffmpeg.SEEK_CUR)
                            bb.position(bb.position() + (int)offset);
                        else if(whence == Ffmpeg.SEEK_END)
                            bb.position(bb.limit() - (int)offset);
                        else if(whence == Ffmpeg.AVSEEK_SIZE)
                            return bb.limit();
                        else
                            return -1;
                        return bb.position();
                    }))
                .videoEncoder("libx265", "first")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .addCodecOptions("flags", "low_delay")
                .setRcBufferSize(4 * 16 * MEG)
                .setRcBitrate(2 * MEG, 3 * MEG)
                .encodingContext()
                .videoEncoder("libx265", "second")
                .addCodecOptions("preset", "ultrafast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .addCodecOptions("flags", "low_delay")
                .setTargetBitrate(2 * MEG)
                .encodingContext()

            ;

            final VideoEncoder ve1 = encoder.getExistingVideoEncoder("first");
            final VideoEncoder ve2 = encoder.getExistingVideoEncoder("second");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num, details[0].fps_den);
                    ve2.setFps(details[0].fps_num, details[0].fps_den);

                })
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
                    if(firstFrame.get()) {
                        firstFrame.set(false);
                        ve1.enable(f, f.isRgb);
                        try(CvMat flipped = new CvMat();) {
                            Core.flip(f, flipped, 1);
                            ve2.enable(flipped, f.isRgb);
                        }
                        encoder.ready();
                    }
                    ve1.encode(f, f.isRgb);
                    try(CvMat flipped = new CvMat();) {
                        Core.flip(f, flipped, 1);
                        ve2.encode(flipped, f.isRgb);
                    }
                    if(id != null) {
                        try(var bgr = f.bgr(false);) {
                            id.update(bgr);
                        }
                    }
                })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        bb.flip();
        try(final var fos = new FileOutputStream(destination);
            final FileChannel fc = fos.getChannel();) {
            fc.write(bb);
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 1000);
        assertTrue(calledSeek.get());
    }

    @Test
    public void testEncodingMjpeg() throws Exception {
        LOGGER.info("Running test: {}.testEncodingMjpeg(sync={})", TestFfmpeg2.class.getSimpleName(), sync);
        final AtomicLong framecount = new AtomicLong(0);
        final File destination = tempDir.newFile("out.mov");
        if(destination.exists())
            destination.delete();

        try(
            final EncodingContext encoder = Ffmpeg.createEncoder();
            final MediaContext ctx = Ffmpeg.createMediaContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .muxer(Muxer.create(destination.getAbsolutePath()))
                .videoEncoder("mjpeg", "first")
                .encodingContext()

            ;

            final VideoEncoder ve1 = encoder.getExistingVideoEncoder("first");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num, details[0].fps_den);
                })
                .chain("default")
                .selectStreams((sd, res) -> {
                    boolean found = false;
                    for(int i = 0; i < sd.length; i++) {
                        res[i] = false;
                        if(!found && sd[i].mediaType == Ffmpeg.AVMEDIA_TYPE_VIDEO) {
                            found = true;
                            res[i] = true;
                        }
                    }

                    return found;
                })
                .processVideoFrames(f -> {
                    final long count = framecount.getAndIncrement();
                    if(count > 300)
                        ctx.stop();
                    if(firstFrame.get()) {
                        firstFrame.set(false);
                        ve1.enable(f, f.isRgb);
                        encoder.ready();
                    }
                    ve1.encode(f, f.isRgb);
                    if(id != null) {
                        try(var bgr = f.bgr(false);) {
                            id.update(bgr);
                        }
                    }
                })
                .mediaContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        assertTrue(frameCount(destination.toURI()) > 250);
    }

    private static long frameCount(final URI uri) {
        final MutableInt ret = new MutableInt(0);
        try(final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;
            final MediaContext c = Ffmpeg.createMediaContext();) {
            c
                .source(uri)
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
                    if(id != null) {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    }
                    ret.val++;
                })
                .mediaContext()
                // .optionally(sync, s -> s.sync())
                .play();
        }
        return ret.val;
    }
}
