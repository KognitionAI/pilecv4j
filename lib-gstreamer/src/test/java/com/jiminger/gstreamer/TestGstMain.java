package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.util.GstUtils;

public class TestGstMain {
   static {
      GstUtils.testMode();
   }

   @Test
   public void testMultipleGstMainCalls() throws Exception {
      try (final GstScope main = new GstScope();) {

         final Pipeline pipe = new BinManager()
               .scope(main)
               .make("videotestsrc")
               .make("fakesink")
               .buildPipeline();

         pipe.play();
         Thread.sleep(300);
         pipe.stop();
      }

      try (final GstScope main = new GstScope();) {

         final Pipeline pipe = new BinManager()
               .scope(main)
               .make("videotestsrc")
               .make("fakesink")
               .buildPipeline();

         pipe.play();
         Thread.sleep(300);
         pipe.stop();
      }
   }
}
