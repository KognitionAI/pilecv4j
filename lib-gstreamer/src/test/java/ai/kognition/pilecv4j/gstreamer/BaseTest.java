package ai.kognition.pilecv4j.gstreamer;

import java.io.File;
import java.net.URI;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

public class BaseTest {

    public static final boolean SHOW;

    static {
        final String sysOpSHOW = System.getProperty("pilecv4j.SHOW");
        final boolean sysOpSet = sysOpSHOW != null;
        boolean show = ("".equals(sysOpSHOW) || Boolean.parseBoolean(sysOpSHOW));
        if(!sysOpSet)
            show = Boolean.parseBoolean(System.getenv("PILECV4J_SHOW"));
        SHOW = show;
    }

    // Dynamically determine if we're at major version 3 or 4 of OpenCV and set the variables appropriately.
    static {
        GstUtils.testMode();
    }

    public final static URI STREAM = new File(
        BaseTest.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();
}
