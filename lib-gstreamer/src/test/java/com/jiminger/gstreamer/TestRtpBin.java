package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Ignore;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;

@Ignore
public class TestRtpBin {
    static {
        GstScope.testMode();
    }

    public final static int PEER_V = 43118; // dynamically assigned ephemeral port
    public final static int SELF_V = 5004;

    @Test
    public void testRtpBinCalls() throws Exception {
        try (final GstScope main = new GstScope();) {

            final Pipeline pipe = new Pipeline();

            final RtpBin h = new RtpBin()
                    .sendSession(1)
                    .rtpFrom(new BinBuilder()
                            .delayed("rtspsrc").with("location", "rtsp://admin:811orrac8@172.16.2.51:554/")
                            // .make("v4l2src")
                            // .delayed("decodebin")
                            .make("queue")
                            // .make("videoconvert")
                            // .make("x264enc").with("tune", "4").with("quantizer", "40")
                            // .make("rtph264pay")
                            // .caps("application/x-rtp,payload=(int)103,clock-rate=(int)90000,ssrc=(uint)" + 112233)
                            .buildBin("main"))
                    .rtpTo(new ElementBuilder("udpsink").with("host", "localhost").with("port", PEER_V)
                            // .with("bind-port", SELF_V)
                            .build())
                    .rtcpSend(new ElementBuilder("udpsink")
                            .with("host", "localhost")
                            .with("port", PEER_V + 1)
                            .with("bind-port", SELF_V + 1)
                            .with("sync", false)
                            .with("async", false)
                            .build())
                    .rtcpRecv(new ElementBuilder("udpsrc").with("port", SELF_V + 1).build());

            h.addTo(pipe);
            h.linkAll();

            pipe.play();

            Thread.sleep(500);
            // GstUtils.printDetails(pipe);

            final Pipeline pipe2 = new BinBuilder()
                    .make("udpsrc").with("port", PEER_V)
                    .caps("application/x-rtp,payload=(int)103,clock-rate=(int)90000,ssrc=(uint)" + 112233)
                    .make("rtph264depay")
                    .delayed("decodebin")
                    .make("videoconvert")
                    .make("xvimagesink")
                    .buildPipeline();

            pipe2.play();

            Gst.main();
        }
    }
}
