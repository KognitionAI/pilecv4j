package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.util.GstUtils.instrument;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.DecodeBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.GstUtils;

public class TestBuildersSimplerPipeline extends BaseTest {

    @Test
    public void testSimplePipeline() throws Exception {
        try (final GstMain m = new GstMain(TestBuildersSimplerPipeline.class);
                final ElementWrap<Pipeline> ew = new BinBuilder()
                        // .make("filesrc").with("location", STREAM.getPath())
                        .make("v4l2src")
                        .delayed(new DecodeBin("source"))
                        .make("fakesink").with("sync", "true")
                        .buildPipeline();) {
            final Pipeline pipe = ew.element;
            instrument(pipe);
            pipe.play();
            Thread.sleep(10000);
            GstUtils.printDetails(pipe);
            pipe.sendEvent(new EOSEvent());
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
            Gst.main();
        }
    }

}
