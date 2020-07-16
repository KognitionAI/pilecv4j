package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

public class TestFrameCatcher extends BaseTest {

    @Test
    public void testFrameCatcher() throws Exception {
        try(GstScope scope = new GstScope();
            final FrameCatcher fc = new FrameCatcher("framecatcher");

            final Pipeline pipe = new BinManager()
                .delayed("uridecodebin").with("uri", STREAM.toString())
                .make("videoconvert")
                .caps("video/x-raw")
                .add(fc.disown())
                .buildPipeline();) {

            pipe.play();
            assertTrue(poll(o -> fc.frames.size() >= 30));
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
        }
    }
}
