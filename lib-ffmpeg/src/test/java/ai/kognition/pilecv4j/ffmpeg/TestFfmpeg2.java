package ai.kognition.pilecv4j.ffmpeg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
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

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.EncodingContext.VideoEncoder;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.StreamContext;
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
        try(StreamContext c = Ffmpeg2.createStreamContext();) {}
    }

    @Test(expected = FfmpegException.class)
    public void testPlayNoSource() {
        try(StreamContext c = Ffmpeg2.createStreamContext();) {
            c
                .optionally(sync, s -> s.sync())
                .play();
        }
    }

    @Test
    public void testPlayWithStop() {
        final AtomicLong frameCount = new AtomicLong(0);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        try(final StreamContext c = Ffmpeg2.createStreamContext();) {
            c
                .createMediaDataSource(STREAM)
                .openChain()
                .createFirstVideoStreamSelector()
                .createVideoFrameProcessor(f -> {
                    if(frameCount.getAndIncrement() > 50L) {
                        LOGGER.debug("Stopping the stream.");
                        c.stop();
                        stopped.set(true);
                    }
                })
                .streamContext()
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
        final AtomicLong frameCount = new AtomicLong(0);
        final MutableRef<Ffmpeg2.StreamContext.StreamDetails[]> details = new MutableRef<>(null);
        try(final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;
            final StreamContext ctx = Ffmpeg2.createStreamContext();) {
            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                // .createMediaDataSource(uncheck(() -> new URI("file:///tmp/out.mp4")))
                .load()
                .peek(s -> {
                    details.ref = s.getStreamDetails();
                    System.out.println(Arrays.toString(details.ref));
                })
                .openChain()
                .createFirstVideoStreamSelector()
                .createVideoFrameProcessor(f -> {
                    frameCount.getAndIncrement();
                    if(SHOW) {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    }
                })
                .streamContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(frameCount.get() > 50);
        assertNotNull(details.ref);
        assertEquals(2, details.ref.length);
        assertEquals(Ffmpeg2.AVMEDIA_TYPE_VIDEO, details.ref[0].mediaType);
        assertEquals(Ffmpeg2.AVMEDIA_TYPE_AUDIO, details.ref[1].mediaType);
    }

    @Test
    public void testCustomDataSource() throws Exception {
        final AtomicLong frameCount = new AtomicLong(0);

        // load the entire file into memory.
        final byte[] contents = FileUtils.readFileToByteArray(STREAM_FILE);
        final MutableInt pos = new MutableInt(0); // track the current position in the stream

        try(

            final StreamContext c = Ffmpeg2.createStreamContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;) {
            c
                .createMediaDataSource(

                    // read bytes from buffer
                    (bb, numBytes) -> {
                        if(pos.val >= contents.length)
                            return Ffmpeg2.AVERROR_EOF_AVSTAT;
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
                        if(whence == Ffmpeg2.SEEK_SET)
                            pos.val = offset;
                        else if(whence == Ffmpeg2.SEEK_CUR)
                            pos.val += offset;
                        else if(whence == Ffmpeg2.SEEK_END)
                            pos.val = contents.length - offset;
                        else if(whence == Ffmpeg2.AVSEEK_SIZE)
                            return contents.length;
                        else
                            return -1;
                        return pos.val;
                    })
                .openChain()
                .createFirstVideoStreamSelector()

                // test the double open
                .streamContext()
                .openChain()

                .createVideoFrameProcessor(f -> {
                    frameCount.getAndIncrement();
                    if(SHOW) {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    }
                })
                .streamContext()
                .optionally(sync, s -> s.sync())
                .play();

            assertTrue(frameCount.get() > 50);
        }
    }

    @Ignore
    @Test
    public void testRemuxStreaming() throws Exception {
        try(

            final StreamContext c = Ffmpeg2.createStreamContext();) {
            c
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                .openChain()
                .createUriRemuxer("flv", "rtmp://localhost:1935/live/feedly-id")
                .streamContext()
                .sync()
                .play();
        }
    }

    @Test
    public void testRemux() throws Exception {
        final File destination = tempDir.newFile("out.flv");
        try(

            final StreamContext c = Ffmpeg2.createStreamContext();) {
            c
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                .openChain()
                .createUriRemuxer(destination.getAbsolutePath())
                .streamContext()
                .optionally(sync, s -> s.sync())
                .play();
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        if(SHOW) {
            try(final ImageDisplay id = new ImageDisplay.Builder().build();
                final StreamContext c = Ffmpeg2.createStreamContext();) {
                c
                    .createMediaDataSource(destination.toURI())
                    .openChain()
                    .createFirstVideoStreamSelector()
                    .createVideoFrameProcessor(f -> {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    })
                    .streamContext()
                    .optionally(sync, s -> s.sync())
                    .play();
            }
        }
    }

    @Test
    public void testEncoding() throws Exception {
        final File destination = tempDir.newFile("out.mp4");
        // final File destination = new File("/tmp/out.mp4");
        if(destination.exists())
            destination.delete();

        try(
            final EncodingContext encoder = Ffmpeg2.createEncoder();
            final StreamContext ctx = Ffmpeg2.createStreamContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .outputStream(destination.getAbsolutePath())
                .openVideoEncoder("libx265", "first")
                .addCodecOptions("preset", "fast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .setBufferSize(4 * 16 * MEG)
                .setBitrate(2 * MEG)
                .encodingContext()
                .openVideoEncoder("libx265", "second")
                .addCodecOptions("preset", "fast")
                .addCodecOptions("x265-params", "keyint=60:min-keyint=60:scenecut=0")
                .setBufferSize(4 * 16 * MEG)
                .setBitrate(2 * MEG)
                .encodingContext()

            ;

            final VideoEncoder ve1 = encoder.getVideoEncoder("first");
            final VideoEncoder ve2 = encoder.getVideoEncoder("second");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                .load()
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setFps(details[0].fps_num / details[0].fps_den);
                    ve2.setFps(details[0].fps_num / details[0].fps_den);

                })
                .openChain()
                .createFirstVideoStreamSelector()
                .createVideoFrameProcessor(f -> {
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
                .streamContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        if(SHOW) {
            try(final ImageDisplay id = new ImageDisplay.Builder().build();
                final StreamContext c = Ffmpeg2.createStreamContext();) {
                c
                    .createMediaDataSource(destination.toURI())
                    .openChain()
                    // with the following commented out, all streams will be selected.
                    // .createFirstVideoStreamSelector()
                    .createVideoFrameProcessor(f -> {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    })
                    .streamContext()
                    .optionally(sync, s -> s.sync())
                    .play();
            }
        }
    }

    @Ignore
    @Test
    public void testEncodingStream() throws Exception {
        try(
            final EncodingContext encoder = Ffmpeg2.createEncoder();
            final StreamContext ctx = Ffmpeg2.createStreamContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .outputStream("flv", "rtmp://localhost:1935/live/feedly-id")
                .openVideoEncoder("libx264", "default")
                .addCodecOptions("profile", "high")
                .addCodecOptions("preset", "superfast")
                // .addCodecOptions("x264-params", "keyint=60:min-keyint=60:scenecut=0")
                // .addCodecOptions("x264-params",
                // "cabac=1:ref=3:deblock=1:0:0:analyse=0x3:0x113:me=hex:subme=7:psy=1:psy_rd=1.00:0.00:mixed_ref=1:me_range=16:chroma_me=1:trellis=1:8x8dct=1:cqm=0:deadzone=21,11:fast_pskip=1:chroma_qp_offset=-2:threads=11:lookahead_threads=1:sliced_threads=0:nr=0:decimate=1:interlaced=0:bluray_compat=0:constrained_intra=0:bframes=3:b_pyramid=2:b_adapt=1:b_bias=0:direct=1:weightb=1:open_gop=0:weightp=2:keyint=250:keyint_min=25:scenecut=40:intra_refresh=0:rc_lookahead=40:rc=crf:mbtree=1:crf=23.0:qcomp=0.60:qpmin=0:qpmax=69:qpstep=4:ip_ratio=1.40:aq=1:1.00")
                .addCodecOptions("tune", "zerolatency")
            // .addCodecOptions("x264-params", "bframes=0")
            // .setBufferSize(4 * 16 * MEG)
            // .setBitrate(2 * MEG)

            ;

            final VideoEncoder ve1 = encoder.getVideoEncoder("default");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            final AtomicLong framecount = new AtomicLong(0);

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
                .streamContext()
                // .sync()
                // .optionally(sync, s -> s.sync())
                .play()

            ;
        }

    }

    // @Ignore
    // @Test
    // public void testEncodingStreamTest() throws Exception {
    // try(
    // final EncodingContext encoder = Ffmpeg2.createEncoder();
    // final StreamContext ctx = Ffmpeg2.createStreamContext();
    // final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;
    //
    // ) {
    //
    // ;
    //
    // // final VideoEncoder ve1 = encoder.getVideoEncoder("default");
    // final AtomicBoolean firstFrame = new AtomicBoolean(true);
    //
    // final AtomicReference<CvMat> ondeck = new AtomicReference<>();
    //
    // ctx
    // .createMediaDataSource(STREAM)
    // .load()
    // // .peek(s -> {
    // // final var details = s.getStreamDetails();
    // // ve1.setFps(details[0].fps_num / details[0].fps_den);
    // //
    // // })
    // .openChain()
    // .createFirstVideoStreamSelector()
    // .createVideoFrameProcessor(f -> {
    // if(firstFrame.get()) {
    // LOGGER.trace("FIRST FRAME: {}", f);
    // firstFrame.set(false);
    // chain(new Thread(() -> FfmpegApi2.startme(f.nativeObj, f.isRgb ? 1 : 0, new get_frame_callback() {
    // @Override
    // public long get_frame() {
    // LOGGER.trace("in get_frame!");
    // CvMat toSend = null;
    // while(toSend == null) {
    // toSend = ondeck.getAndSet(null);
    // if(toSend == null)
    // uncheck(() -> Thread.sleep(1));
    // }
    //
    // if(id != null) {
    // id.update(toSend);
    // }
    //
    // LOGGER.trace("returning {} from get_frame", toSend.nativeObj);
    // return toSend.nativeObj;
    // }
    // }), "test junk"), t -> t.setDaemon(true), t -> t.start());
    // uncheck(() -> Thread.sleep(100)); // HACK!
    // }
    //
    // try(CvMat mat = CvMat.shallowCopy(f);
    // CvMat leftover = ondeck.getAndSet(mat.returnMe());) {
    //
    // }
    //
    // // if(id != null) {
    // // try(var bgr = f.bgr(false);) {
    // // id.update(bgr);
    // // }
    // // }
    // })
    // .streamContext()
    // .sync()
    // // .optionally(sync, s -> s.sync())
    // .play()
    //
    // ;
    // }
    //
    // }

    @Test
    public void testEncodingMjpeg() throws Exception {
        final AtomicLong framecount = new AtomicLong(0);
        final File destination = tempDir.newFile("out.mov");
        // final File destination = new File("/tmp/out.mov");
        if(destination.exists())
            destination.delete();

        try(
            final EncodingContext encoder = Ffmpeg2.createEncoder();
            final StreamContext ctx = Ffmpeg2.createStreamContext();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;

        ) {

            encoder
                .outputStream(destination.getAbsolutePath())
                .openVideoEncoder("mjpeg", "first")
                .encodingContext()

            ;

            final VideoEncoder ve1 = encoder.getVideoEncoder("first");
            final AtomicBoolean firstFrame = new AtomicBoolean(true);

            ctx
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                // .createMediaDataSource("rtsp://admin:gregormendel1@172.16.2.11:554/")
                .load()
                .peek(s -> {
                    final var details = s.getStreamDetails();
                    ve1.setEncodingParameters(details[0].fps_num / details[0].fps_den, 4 * 16 * MEG, 8 * MEG, 8 * MEG);
                })
                .openChain()
                .createStreamSelector((sd, res) -> {
                    boolean found = false;
                    for(int i = 0; i < sd.length; i++) {
                        res[i] = false;
                        if(!found && sd[i].mediaType == Ffmpeg2.AVMEDIA_TYPE_VIDEO) {
                            found = true;
                            res[i] = true;
                        }
                    }

                    return found;
                })
                .createVideoFrameProcessor(f -> {
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
                .streamContext()
                .optionally(sync, s -> s.sync())
                .play()

            ;
        }

        assertTrue(destination.exists());
        assertTrue(destination.isFile());
        assertTrue(destination.length() > 0);

        if(SHOW) {
            try(final ImageDisplay id = new ImageDisplay.Builder().build();
                final StreamContext c = Ffmpeg2.createStreamContext();) {
                c
                    .createMediaDataSource(destination.toURI())
                    .openChain()
                    .createFirstVideoStreamSelector()
                    .createVideoFrameProcessor(f -> {
                        try(final CvMat rgb = f.bgr(false);) {
                            id.update(rgb);
                        }
                    })
                    .streamContext()
                    .optionally(sync, s -> s.sync())
                    .play();
            }
        }

    }
}
