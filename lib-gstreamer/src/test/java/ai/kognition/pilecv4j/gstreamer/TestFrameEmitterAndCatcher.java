package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;
import ai.kognition.pilecv4j.gstreamer.util.FrameEmitter;

public class TestFrameEmitterAndCatcher extends BaseTest {

    @Test
    public void testFrameEmitterToCather() throws Exception {
        try(GstScope scope = new GstScope();
            final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 30);
            final FrameCatcher fc = new FrameCatcher("framecatcher");

            final Pipeline pipe = new BinManager()
                .add(fe.disown())
                .make("videoconvert")
                .caps("video/x-raw")
                .add(fc.disown())
                .buildPipeline();) {

            pipe.play();
            assertTrue(poll(o -> fe.isDone()));
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
            // this should be 30 but occasionally we get only 29
            assertTrue(poll(o -> fc.numCaught() >= 29));
        }
    }
}
