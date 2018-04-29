package com.jiminger.gstreamer;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BreakoutFilter.CvRasterAndCaps;
import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.FrameEmitter;

public class TestSlowFrameProcessing {
    static {
        GstScope.testMode();
    }

    private final static Logger LOGGER = LoggerFactory.getLogger(TestSlowFrameProcessing.class);
    final static URI STREAM = new File(
            TestFrameCatcherUnusedCleansUp.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

    @Test
    public void testSlowFrameProcessing() throws Exception {
        final AtomicLong slowFramesProcessed = new AtomicLong(0);
        try (final GstScope m = new GstScope(TestSlowFrameProcessing.class);
                final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 60);
                final FrameCatcher fc = new FrameCatcher("framecatcher");) {

            final Pipeline pipe = new BinBuilder()
                    .add(fe.disown())
                    .make("videoconvert")
                    .caps("video/x-raw")
                    .add(new BreakoutFilter("filter1")
                            .connectSlowFilter((final CvRasterAndCaps bac) -> {
                                if (FrameEmitter.HACK_FRAME)
                                    LOGGER.trace("byte0 " + bac.raster.underlying.get(0));
                                uncheck(() -> Thread.sleep(100)); // ~10 frame/second
                                slowFramesProcessed.incrementAndGet();
                            }))
                    .add(fc.disown())
                    .buildPipeline(m);

            // instrument(pipe);
            pipe.play();
            assertTrue(poll(o -> fe.isDone()));
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
            assertEquals(60, fc.frames.size());
            // the slowFramesProcessed should be less than 1/2 of the processed frames
            assertTrue(30 > slowFramesProcessed.get());
        }
    }

}
