package ai.kognition.pilecv4j.ffmpeg;

import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.MutableInt;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.StreamContext;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class TestFfmpeg extends BaseTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestFfmpeg.class);

    @Test
    public void testInit() {
        FfmpegApi.pcv4j_ffmpeg_init();
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
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;) {
            c.setLogLevel(LOGGER);
            c.addOption("rtsp_flags", "prefer_tcp");
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

    @Test
    public void testSendFrames() throws Exception {
        final AtomicLong frameCount = new AtomicLong(0);

        // load the entire file into memory.
        final byte[] contents = FileUtils.readFileToByteArray(STREAM_FILE);
        final MutableInt pos = new MutableInt(0); // track the current position in the stream

        try(final StreamContext c = Ffmpeg.createStreamContext();
            final ImageDisplay id = new ImageDisplay.Builder().build();) {
            c.setLogLevel(LoggerFactory.getLogger(this.getClass()));
            c.openStream(

                // read bytes from buffer
                (bb, numBytes) -> {
                    if(pos.val >= contents.length)
                        return Ffmpeg.AVERROR_EOF;
                    final int size = bb.capacity();
                    LOGGER.trace("buf size: {}, to write: {}, from pos: {}", size, numBytes, pos.val);
                    bb.rewind();
                    int numToSend = numBytes > bb.capacity() ? bb.capacity() : numBytes;
                    if(numToSend + (int)pos.val > contents.length) {
                        numToSend = contents.length - (int)pos.val;
                    }
                    LOGGER.trace("contents read from {} to {}({})", pos.val, (pos.val + numToSend), numToSend);
                    bb.put(contents, (int)pos.val, numToSend);
                    pos.val += numToSend;
                    if(pos.val >= contents.length)
                        LOGGER.debug("Last bytes being sent!");
                    return numToSend;
                },

                // need to support seek to read some mp4 files
                (final ByteBuffer bb, final long offset, final int whence) -> {
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
                }

            );

            if(SHOW)
                c.sync(true);

            c.processFrames(f -> {
                frameCount.getAndIncrement();
                if(SHOW) {
                    try(final CvMat rgb = f.bgr(false);) {
                        id.update(rgb);
                    }
                }
            });

            assertTrue(frameCount.get() > 50);
        }
    }

}
