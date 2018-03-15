package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;

import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BreakoutFilter.CvRasterAndCaps;
import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.FrameEmitter;

public class TestBreakoutPassthrough {
    private final static Logger LOGGER = LoggerFactory.getLogger(TestBreakoutPassthrough.class);
    final static URI STREAM = new File(
            TestBuilders.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

    @Test
    public void testBreakoutWithPassthrough() throws Exception {
        try (final GstMain m = new GstMain(TestBreakoutPassthrough.class);) {
            final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 30);
            final FrameCatcher fc = new FrameCatcher("framecatcher");

            try (final ElementWrap<Pipeline> ew = new ElementWrap<>(new BinBuilder()
                    .add(fe.element)
                    .make("videoconvert")
                    .caps("video/x-raw")
                    .add(new BreakoutFilter("filter")
                            .connect((final CvRasterAndCaps bac) -> {
                                if (FrameEmitter.HACK_FRAME)
                                    LOGGER.trace("byte0 " + bac.raster.underlying.get(0));
                                return FlowReturn.OK;
                            }))
                    .add(new BreakoutFilter("filter1")
                            .connect((final CvRasterAndCaps bac) -> {
                                if (FrameEmitter.HACK_FRAME)
                                    LOGGER.trace("byte0 " + bac.raster.underlying.get(0));
                                return FlowReturn.OK;
                            }))
                    .add(new BreakoutFilter("filter2")
                            .connect((final CvRasterAndCaps bac) -> {
                                if (FrameEmitter.HACK_FRAME)
                                    LOGGER.trace("byte0 " + bac.raster.underlying.get(0));
                                return FlowReturn.OK;
                            }))
                    .add(fc.sink)
                    .buildPipeline());) {

                final Pipeline pipe = ew.element;
                // instrument(pipe);
                pipe.play();
                assertTrue(poll(o -> fe.isDone()));
                Thread.sleep(500);
                pipe.stop();
                assertTrue(poll(o -> !pipe.isPlaying()));

                assertEquals(30, fc.frames.size());
            }
        }
    }
}
