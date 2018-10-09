package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.uncheck;
import static net.dempsy.utils.test.ConditionPoll.poll;

import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.RtspServer;
import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

import static org.junit.Assert.assertTrue;

public class TestRtspServerMain extends BaseTest {

   @Test
   public void test() throws Exception {

      final String fullPath = TestRtspServerMain.class.getClassLoader().getResource("rtsp-server/rtsp-server.properties").getFile();

      final Thread thread = new Thread(() -> uncheck(() -> RtspServer.main(new String[] {fullPath})), "RtspServer");
      thread.setDaemon(true);
      thread.start();

      try (final GstScope scope = new GstScope();
            final FrameCatcher fc = new FrameCatcher("frame-catcher");) {

         new BinManager()
               .scope(scope)
               .delayed("uridecodebin").with("uri", "rtsp://localhost:8554/test")
               .make("videoconvert")
               .add(fc.disown())
               .buildPipeline()
               .play();

         assertTrue(poll(o -> fc.frames.size() > 20));
      } finally {
         RtspServer.stopMain();
         thread.join(5000);
         assertTrue(!thread.isAlive());
      }
   }
}
