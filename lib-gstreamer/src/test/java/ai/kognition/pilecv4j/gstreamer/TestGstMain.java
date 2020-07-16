package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;

public class TestGstMain {
    @Test
    public void testMultipleGstMainCalls() throws Exception {
        try(GstScope scope = new GstScope();
            final Pipeline pipe = new BinManager()
                .make("videotestsrc")
                .make("fakesink")
                .buildPipeline();) {

            pipe.play();
            Thread.sleep(300);
            pipe.stop();
        }

        try(GstScope scope = new GstScope();
            final Pipeline pipe = new BinManager()
                .make("videotestsrc")
                .make("fakesink")
                .buildPipeline();) {

            pipe.play();
            Thread.sleep(300);
            pipe.stop();
        }
    }
}
