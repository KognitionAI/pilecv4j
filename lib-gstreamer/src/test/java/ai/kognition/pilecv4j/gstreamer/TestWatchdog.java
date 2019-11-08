package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.ignore;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;
import ai.kognition.pilecv4j.gstreamer.util.FrameEmitter;

public class TestWatchdog extends BaseTest {

    @Test
    public void testBreakoutFilterLoad() throws Exception {
        try (final GstScope main = new GstScope();) {
            final AtomicBoolean hit = new AtomicBoolean(false);
            final BreakoutFilter breakout = (BreakoutFilter)ElementFactory.make("breakout", "breakout");
            breakout.addWatchdog(1000, () -> hit.set(true));

            try (final FrameCatcher fc = new FrameCatcher("framecatcher");
                final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 40) {
                    // prevent the EOS from being sent.
                    @Override
                    public boolean isDone() {
                        boolean ret = super.isDone();
                        if(ret)
                            ignore(() -> Thread.sleep(2000));
                        return false;
                    }
                };) {

                final Pipeline pipe = new BinManager()
                    .scope(main)
                    .add(fe.disown())
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .buildString())
                    .make("queue", "namedQueuey")
                    .add(breakout)
                    .make("videoconvert")
                    .add(fc.disown())
                    .stopOnEndOfStream()
                    .buildPipeline();

                pipe.play();

                assertTrue(poll(o -> fc.frames.size() == 40));
                Thread.sleep(10);
                assertEquals(40, fc.frames.size());

                assertTrue(poll(o -> hit.get()));

                if(pipe.isPlaying())
                    pipe.stop();
                assertTrue(poll(o -> !pipe.isPlaying()));
            }
        }
    }

}
