package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.GstUtils;

import static org.junit.Assert.assertTrue;

public class TestBuildersPipelineWithNamedDelayed extends BaseTest {

   @Test
   public void testNamedDelayed() throws Exception {
      try (final GstScope m = new GstScope(TestFrameEmitterAndCatcher.class);
            final FrameCatcher fc = new FrameCatcher("framecatcher");) {

         final Pipeline pipe = new BinManager()
               .scope(m)
               .delayed("uridecodebin", "myjunkyderidecodeybin").with("uri", STREAM.toString())
               .make("videoconvert")
               .caps("video/x-raw")
               .add(fc.disown())
               .buildPipeline();

         pipe.play();
         assertTrue(poll(o -> fc.frames.size() >= 30));
         GstUtils.printDetails(pipe);
         pipe.stop();
         assertTrue(poll(o -> !pipe.isPlaying()));
      }
   }
}
