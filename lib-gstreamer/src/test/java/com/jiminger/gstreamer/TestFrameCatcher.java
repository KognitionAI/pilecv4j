package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;

public class TestFrameCatcher extends BaseTest {

    @Test
    public void testFrameCatcher() throws Exception {
        try (final GstMain m = new GstMain(TestFrameCatcher.class);
                final FrameCatcher fc = new FrameCatcher("framecatcher");
                ElementWrap<Pipeline> ew = new BinBuilder()
                        .delayed("uridecodebin").with("uri", STREAM.toString())
                        .make("videoconvert")
                        .caps("video/x-raw")
                        .add(fc.disown())
                        .buildPipeline();) {
            final Pipeline pipe = ew.element;
            pipe.play();
            assertTrue(poll(o -> fc.frames.size() >= 30));
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
        }
    }
}
