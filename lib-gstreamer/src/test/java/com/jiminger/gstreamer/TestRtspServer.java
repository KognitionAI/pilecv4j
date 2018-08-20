package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;

import static org.junit.Assert.assertTrue;

public class TestRtspServer extends BaseTest {

   @Test
   public void test() throws Exception {

      try (final GstScope main = new GstScope();
            final RtspServer server = new RtspServer();
            final FrameCatcher fc = new FrameCatcher("frame-catcher");) {
         server.startServer();
         server.play(BaseTest.STREAM.toString(), "/test");

         // now we need to connect to it.
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
