package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.GstUtils;

public class TestBuildersPipelineWithNamedDelayed extends BaseTest {

    @Test
    public void testNamedDelayed() throws Exception {
        try (final GstMain m = new GstMain(TestFrameEmitterAndCatcher.class);
                final FrameCatcher fc = new FrameCatcher("framecatcher");
                final ElementWrap<Pipeline> ew = new BinBuilder()
                        .delayed("uridecodebin", "myjunkyderidecodeybin").with("uri", STREAM.toString())
                        .make("videoconvert")
                        .caps("video/x-raw")
                        .add(fc.disown())
                        .buildPipeline();) {
            final Pipeline pipe = ew.element;
            pipe.play();
            assertTrue(poll(o -> fc.frames.size() >= 30));
            GstUtils.printDetails(pipe);
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
        }
    }
}
