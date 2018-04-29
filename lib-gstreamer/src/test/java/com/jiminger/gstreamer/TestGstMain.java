package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;

public class TestGstMain {
    static {
        GstScope.testMode();
    }

    @Test
    public void testMultipleGstMainCalls() throws Exception {
        try (final GstScope main = new GstScope();) {

            final Pipeline pipe = new BinBuilder()
                    .make("videotestsrc")
                    .make("fakesink")
                    .buildPipeline(main);

            pipe.play();
            Thread.sleep(300);
            pipe.stop();
        }

        try (final GstScope main = new GstScope();) {

            final Pipeline pipe = new BinBuilder()
                    .make("videotestsrc")
                    .make("fakesink")
                    .buildPipeline(main);

            pipe.play();
            Thread.sleep(300);
            pipe.stop();
        }
    }
}
