package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.GstUtils.instrument;

import java.io.File;
import java.net.URI;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.junit.Test;

public class TestBuilders {
    final static URI STREAM = new File(
            TestBuilders.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

    @Test
    public void testSimplePipeline() throws Exception {
        Gst.init(TestBuilders.class.getSimpleName(), new String[] {});

        final Pipeline pipe = new BinBuilder()
                .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
                .make("videoscale")
                .make("videoconvert")
                .caps("video/x-raw,width=640,height=480")
                .make("xvimagesink")
                .buildPipeline();

        instrument(pipe);
        pipe.play();
        Thread.sleep(1000);
        pipe.stop();

    }

}
