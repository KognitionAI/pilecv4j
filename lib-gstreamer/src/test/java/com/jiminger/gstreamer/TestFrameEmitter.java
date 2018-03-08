package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.FrameEmitter;

public class TestFrameEmitter {
    final static URI STREAM = new File(
            TestBuilders.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();
    // public static final String STREAM = "file:///home/jim/Videos/Dave Smith Libertas (2017).mp4";

    @Test
    public void testFrameEmitterToCather() throws Exception {
        try (final GstMain m = new GstMain(TestFrameEmitter.class);) {
            final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 30);
            final FrameCatcher fc = new FrameCatcher("framecatcher");
            try (ElementWrap<Pipeline> ew = new ElementWrap<>(new BinBuilder()
                    .add(fe.element)
                    .make("videoconvert")
                    .caps("video/x-raw")
                    .add(fc.sink)
                    .buildPipeline());) {
                final Pipeline pipe = ew.element;
                pipe.play();
                assertTrue(poll(o -> fe.isDone()));
                pipe.stop();
                assertTrue(poll(o -> !pipe.isPlaying()));
                Thread.sleep(500);
                assertEquals(30, fc.frames.size());
            }
        }
    }
}
