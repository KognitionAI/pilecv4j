package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;

import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.DecodeBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

import static org.junit.Assert.assertTrue;

public class TestBuildersSimplerPipeline extends BaseTest {

   @Test
   public void testSimplePipeline() throws Exception {
      try (final GstScope m = new GstScope(TestBuildersSimplerPipeline.class);) {

         final Pipeline pipe = new BinManager()
               .scope(m)
               .make("filesrc").with("location", STREAM.getPath())
               .delayed(new DecodeBin("source"))
               .make("fakesink").with("sync", "true")
               .stopOnEndOfStream()
               .buildPipeline();

         pipe.play();
         Thread.sleep(2000);

         GstUtils.printDetails(pipe);

         pipe.sendEvent(new EOSEvent());

         assertTrue(poll(o -> !pipe.isPlaying()));
      }
   }

}
