package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.RtspServer;
import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

import static org.junit.Assert.assertTrue;

public class TestRtspServer extends BaseTest {

   @Test
   public void test() throws Exception {

      try (final GstScope main = new GstScope();
            final RtspServer server = new RtspServer();
            final FrameCatcher fc = new FrameCatcher("frame-catcher");) {
         server.startServer(8554);
         server.play(STREAM.toString(), "/test");
         System.out.println(STREAM);

         new BinManager()
               .scope(main)
               .delayed("uridecodebin").with("uri", "rtsp://localhost:8554/test")
               .make("videoconvert")
               .add(fc.disown())
               .buildPipeline()
               .play();

         assertTrue(poll(o -> fc.frames.size() > 20));
      }
   }
}
