package ai.kognition.pilecv4j.gstreamer;

import java.io.File;
import java.net.URI;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

public class BaseTest {
   static {
      GstUtils.testMode();
   }

   public final static URI STREAM = new File(
         BaseTest.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

}
