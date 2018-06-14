package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;

public class TestBuildersEndOnError extends BaseTest {

    @Test
    public void testEndOnError() throws Exception {
        try (final GstScope m = new GstScope(TestFrameEmitterAndCatcher.class);
                final FrameCatcher fc = new FrameCatcher("framecatcher");) {

            final AtomicBoolean gotHere = new AtomicBoolean(false);
            final Pipeline pipe = new BinManager()
                    .delayed("uridecodebin", "myjunkyderidecodeybin").with("uri", STREAM.toString())
                    // intentional mismatch between decodebin output and the FrameCatcher without a videoconvert
                    .add(fc.disown())
                    .onError((x, y, z) -> gotHere.set(true))
                    .buildPipeline(m);

            pipe.play();
            assertTrue(poll(o -> gotHere.get()));
            pipe.stop();
        }
    }
}
