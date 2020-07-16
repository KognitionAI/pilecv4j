package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Ignore;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

@Ignore
public class TestRtpBin {
    static {
        GstUtils.testMode();
    }

    public final static int PEER_V = 43118; // dynamically assigned ephemeral port
    public final static int SELF_V = 5004;

    @Test
    public void testRtpBinCalls() throws Exception {
        try(GstScope scope = new GstScope();
            final Pipeline pipe = new Pipeline();) {

            final RtpBin h = new RtpBin()
                .sendSession(1)
                .rtpFrom(new BinManager()
                    .make("v4l2src")
                    .delayed("decodebin")
                    .make("queue")
                    .buildBin("main"))
                .rtpTo(new ElementBuilder("udpsink").with("host", "localhost").with("port", PEER_V)
                    .build())
                .rtcpSend(new ElementBuilder("udpsink")
                    .with("host", "localhost")
                    .with("port", PEER_V + 1)
                    .with("bind-port", SELF_V + 1)
                    .with("sync", false)
                    .with("async", false)
                    .build())
                .rtcpRecv(new ElementBuilder("udpsrc").with("port", SELF_V + 1).build());

            h.conditionallyAddTo(pipe);
            h.conditionallyLinkAll();

            pipe.play();

            Thread.sleep(500);
            GstUtils.printDetails(pipe);

            final Pipeline pipe2 = new BinManager()
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
