package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher.Frame;
import ai.kognition.pilecv4j.image.VideoFrame;
import ai.kognition.pilecv4j.gstreamer.util.FrameEmitter;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

public class TestBreakoutFilter extends BaseTest {

    @Test
    public void testBreakoutFilterLoad() throws Exception {
        try(GstScope scope = new GstScope();) {
            final BreakoutFilter breakout = (BreakoutFilter)ElementFactory.make("breakout", "breakout");
            breakout.filter(mac -> {
                final VideoFrame mat = mac;
                final int thickness = 5;
                Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                    new Point(0.1 * mat.width(), 0.1 * mat.height()),
                    new Scalar(0xff, 0xff, 0xff), thickness);
            });

            List<Frame> frames = null;

            try(final FrameCatcher fc = new FrameCatcher("framecatcher", true);
                final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 40);

                final Pipeline pipe = new BinManager()
                    .add(fe.disown())
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .buildString())
                    .make("queue", "namedQueuey")
                    .add(breakout)
                    .make("videoconvert")
                    .add(fc.disown())
                    .stopOnEndOfStream()
                    .buildPipeline();) {

                pipe.play();
                Thread.sleep(1000);
                GstUtils.printDetails(pipe);

                assertTrue(poll(o -> fc.numCaught() == 40));
                Thread.sleep(10);
                assertEquals(40, fc.numCaught());

                pipe.stop();
                assertTrue(poll(o -> !pipe.isPlaying()));
                Thread.sleep(100);

                frames = new ArrayList<>(fc.frames());
            }

            final AtomicInteger checked = new AtomicInteger(0);
            // assumes all frames are the same.
            final Frame field = frames.get(0);
            final int left = (int)Math.round(0.1 * field.w);
            final int right = (int)Math.round(0.9 * field.w);
            final int top = (int)Math.round(0.1 * field.h);
            final int bot = (int)Math.round(0.9 * field.h);
            frames.stream().forEach(f -> {
                // check the top lone

                // make sure the top of the rectangle is white as
                // well as the bottom.
                for(int col = left; col < right; col++) {
                    // channels/pixel = 3.
                    final int topByte = ((top * f.w) + col) * 3;
                    final int botByte = ((bot * f.w) + col) * 3;
                    assertEquals(-1, f.data[topByte]);
                    assertEquals(-1, f.data[topByte + 1]);
                    assertEquals(-1, f.data[topByte + 2]);
                    assertEquals(-1, f.data[botByte]);
                    assertEquals(-1, f.data[botByte + 1]);
                    assertEquals(-1, f.data[botByte + 2]);
                    checked.addAndGet(2);
                }
                // make sure the left side of the rectangle is white as
                // well as the right side.
                for(int row = top; row < bot; row++) {
                    // channels/pixel = 3.
                    final int leftByte = ((row * f.w) + left) * 3;
                    final int rightByte = ((row * f.w) + right) * 3;
                    assertEquals(-1, f.data[leftByte]);
                    assertEquals(-1, f.data[leftByte + 1]);
                    assertEquals(-1, f.data[leftByte + 2]);
                    assertEquals(-1, f.data[rightByte]);
                    assertEquals(-1, f.data[rightByte + 1]);
                    assertEquals(-1, f.data[rightByte + 2]);
                    checked.addAndGet(2);
                }
            });
            assertEquals((40 /* frames */
                * 2 /* top and bottom rows */
                * (right - left) /* length of a row of white pixels */) +
                (40 /* frames */
                    * 2 /* right and left cols */
                    * (bot - top) /* length of a row of white pixels */),
                checked.get());
        }
    }

}
