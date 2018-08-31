package com.jiminger.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.FrameCatcher;

import static org.junit.Assert.assertTrue;

public class TestBuildersSimplePipeline extends BaseTest {

   @Test
   public void testSimplePipeline() throws Exception {
      try (final GstScope m = new GstScope(TestBuildersSimplePipeline.class);
            final FrameCatcher fc = new FrameCatcher("framecatcher");) {

         final Pipeline pipe = new BinManager()
               .scope(m)
               .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
               .make("videoscale")
               .make("videoconvert")
               .caps("video/x-raw,width=640,height=480")
               .add(fc.disown())
               .buildPipeline();

         pipe.play();
         // wait until at least 1 frame goes through.
         poll(o -> fc.frames.size() > 0);
         // mark the # of frames to start.
         final int startingNumFrames = fc.frames.size();
         // wait 1 second.
         Thread.sleep(1000);
         // stop
         pipe.sendEvent(new EOSEvent());
         pipe.stop();
         // wait until pipe stops
         assertTrue(poll(o -> !pipe.isPlaying()));

         // one second worth of frames should be more than 25 and less than 35 (actually, should be 29)
         final int numFrames = fc.frames.size() - startingNumFrames;
         assertTrue("Number of frames(" + numFrames + ") was less than 25.", 25 < numFrames);
         assertTrue("Number of frames(" + numFrames + ") was greater than 35.", 35 > numFrames);
      }
   }

}
