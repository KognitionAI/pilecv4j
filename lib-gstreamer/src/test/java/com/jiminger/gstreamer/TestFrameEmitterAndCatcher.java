package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.FrameEmitter;

public class TestFrameEmitterAndCatcher extends BaseTest {

    @Test
    public void testFrameEmitterToCather() throws Exception {
        try (final GstScope m = new GstScope(TestFrameEmitterAndCatcher.class);
                final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 30);
                final FrameCatcher fc = new FrameCatcher("framecatcher");) {

            final Pipeline pipe = new BinManager()
                    .add(fe.disown())
                    .make("videoconvert")
                    .caps("video/x-raw")
                    .add(fc.disown())
                    .buildPipeline(m);

            pipe.play();
            assertTrue(poll(o -> fe.isDone()));
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
            Thread.sleep(500);
            assertEquals(30, fc.frames.size());
        }
    }
}
