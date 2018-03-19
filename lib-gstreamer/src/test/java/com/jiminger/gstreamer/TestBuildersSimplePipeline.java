package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.util.GstUtils.instrument;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;

public class TestBuildersSimplePipeline extends BaseTest {

    @Test
    public void testSimplePipeline() throws Exception {
        try (final GstMain m = new GstMain(TestBuildersSimplePipeline.class);
                final FrameCatcher fc = new FrameCatcher("framecatcher");
                final ElementWrap<Pipeline> ew = new BinBuilder()
                        .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
                        .make("videoscale")
                        .make("videoconvert")
                        .caps("video/x-raw,width=640,height=480")
                        .add(fc.disown())
                        .buildPipeline();) {
            final Pipeline pipe = ew.element;
            instrument(pipe);
            pipe.play();
            Thread.sleep(1000);
            pipe.sendEvent(new EOSEvent());
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));

            // one seconds worth of frames should be more than 25 and less than 35 (actually, should be 29)
            final int numFrames = fc.frames.size();
            assertTrue(20 < numFrames);
            assertTrue(35 > numFrames);
        }
    }

}
