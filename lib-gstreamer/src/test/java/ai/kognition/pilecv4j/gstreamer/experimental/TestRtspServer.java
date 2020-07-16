package ai.kognition.pilecv4j.gstreamer.experimental;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BaseTest;
import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.GstScope;
import ai.kognition.pilecv4j.gstreamer.experimental.RtspServer;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

// TODO: Get this working on Windows. It already works on Linux
@Ignore
public class TestRtspServer extends BaseTest {

    @Test
    public void test() throws Exception {

        try(GstScope scope = new GstScope();
            final RtspServer server = new RtspServer();
            final FrameCatcher fc = new FrameCatcher("frame-catcher");) {
            server.startServer(8554);
            server.play(STREAM.toString(), "/test");
            System.out.println(STREAM);

            new BinManager()
                .delayed("uridecodebin").with("uri", "rtsp://localhost:8554/test")
                .make("videoconvert")
                .add(fc.disown())
                .buildPipeline()
                .play();

            assertTrue(poll(o -> fc.frames.size() > 20));
        }
    }
}
