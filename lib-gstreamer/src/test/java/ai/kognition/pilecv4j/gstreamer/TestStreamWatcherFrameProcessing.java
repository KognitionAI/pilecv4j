package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;
import ai.kognition.pilecv4j.gstreamer.util.FrameEmitter;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;
import ai.kognition.pilecv4j.image.VideoFrame;

public class TestStreamWatcherFrameProcessing {
    static {
        GstUtils.testMode();
    }

    private final static Logger LOGGER = LoggerFactory.getLogger(TestStreamWatcherFrameProcessing.class);
    final static URI STREAM = new File(
        TestFrameCatcherUnusedCleansUp.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

    @After
    public void after() {
        for(int i = 0; i < 100; i++) {
            System.gc();
            uncheck(() -> Thread.sleep(10));
        }
        LOGGER.info("Done!");
    }

    @Test
    public void testSlowFrameProcessing() throws Exception {
        final AtomicLong slowFramesProcessed = new AtomicLong(0);
        try(GstScope scope = new GstScope();
            final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 60);
            final FrameCatcher fc = new FrameCatcher("framecatcher");

            @SuppressWarnings("resource")
            final Pipeline pipe = new BinManager()
                .add(fe.disown())
                .make("videoconvert")
                .caps("video/x-raw")
                .add(new BreakoutFilter("filter1")
                    .streamWatcher((final VideoFrame bac) -> {
                        if(FrameEmitter.HACK_FRAME)
                            LOGGER.trace("byte0 " + bac.rasterOp(r -> r.underlying().get(0)));
                        System.out.println("" + bac);
                        try {
                            Thread.sleep(100); // ~10 frame/second
                            slowFramesProcessed.incrementAndGet();
                        } catch(InterruptedException ie) {
                            LOGGER.trace("Interrupted slow frame processing (assuming for exiting).");
                        }
                    }))
                .add(fc.disown())
                .buildPipeline();) {

            // instrument(pipe);
            pipe.play();
            assertTrue(poll(o -> fe.isDone()));
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
            assertEquals(60, fc.numCaught());
            // the slowFramesProcessed should be less than 1/2 of the processed frames
            assertTrue(30 > slowFramesProcessed.get());
        }
    }

}
