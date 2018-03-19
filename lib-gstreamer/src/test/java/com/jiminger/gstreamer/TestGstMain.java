package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;

public class TestGstMain {
    static {
        GstMain.testMode();
    }

    @Test
    public void testMultipleGstMainCalls() throws Exception {
        try (final GstMain main = new GstMain();
                final ElementWrap<Pipeline> pipew = new BinBuilder()
                        .make("videotestsrc")
                        .make("fakesink")
                        .buildPipeline();) {
            final Pipeline pipe = pipew.element;
            pipe.play();
            Thread.sleep(300);
            pipe.stop();
        }

        try (final GstMain main = new GstMain();
                final ElementWrap<Pipeline> pipew = new BinBuilder()
                        .make("videotestsrc")
                        .make("fakesink")
                        .buildPipeline();) {
            final Pipeline pipe = pipew.element;
            pipe.play();
            Thread.sleep(300);
            pipe.stop();
        }
    }
}
