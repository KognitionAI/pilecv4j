package ai.kognition.pilecv4j.gstreamer.experimental;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BaseTest;
import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

// TODO: Get this working on Windows. It already works on Linux
@Ignore
public class TestRtspServerMain extends BaseTest {

    @Test
    public void test() throws Exception {
        try(final GstScope scope = new GstScope();) {

            final String fullPath = TestRtspServerMain.class.getClassLoader().getResource("rtsp-server/rtsp-server.properties").getFile();

            final Thread thread = new Thread(() -> uncheck(() -> RtspServer.main(new String[] {fullPath})), "RtspServer");
            thread.setDaemon(true);
            thread.start();

            try(final FrameCatcher fc = new FrameCatcher("frame-catcher", false);) {

                new BinManager()
                    .delayed("uridecodebin").with("uri", "rtsp://localhost:8554/test")
                    .make("videoconvert")
                    .add(fc.disown())
                    .buildPipeline()
                    .play();

                assertTrue(poll(o -> fc.numCaught() > 20));
            } finally {
                RtspServer.stopMain();
                thread.join(5000);
                assertTrue(!thread.isAlive());
            }
        }
    }
}
