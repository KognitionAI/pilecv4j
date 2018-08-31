package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;

import static org.junit.Assert.assertTrue;

public class TestFrameCatcher extends BaseTest {

   @Test
   public void testFrameCatcher() throws Exception {
      try (final GstScope m = new GstScope(TestFrameCatcher.class);
            final FrameCatcher fc = new FrameCatcher("framecatcher");) {

         final Pipeline pipe = new BinManager()
               .scope(m)
               .delayed("uridecodebin").with("uri", STREAM.toString())
               .make("videoconvert")
               .caps("video/x-raw")
               .add(fc.disown())
               .buildPipeline();

         pipe.play();
         assertTrue(poll(o -> fc.frames.size() >= 30));
         pipe.stop();
         assertTrue(poll(o -> !pipe.isPlaying()));
      }
   }
}
