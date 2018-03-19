package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.GstUtils;

public class TestBuildersEndOnError extends BaseTest {

    @Test
    public void testEndOnError() throws Exception {
        try (final GstMain m = new GstMain(TestFrameEmitterAndCatcher.class);
                final FrameCatcher fc = new FrameCatcher("framecatcher");
                final ElementWrap<Pipeline> pipew = new BinBuilder()
                        .delayed("uridecodebin", "myjunkyderidecodeybin").with("uri", STREAM.toString())
                        .make("videoconvert")
                        .caps("video/x-raw,format=RGB")
                        .add(fc.disown())
                        .buildPipeline();) {
            final Pipeline pipe = pipew.element;
            final AtomicBoolean gotHere = new AtomicBoolean(false);
            GstUtils.instrument(pipew, (x, y, z) -> gotHere.set(true));
            pipe.play();
            assertTrue(poll(o -> gotHere.get()));
        }
    }

}
