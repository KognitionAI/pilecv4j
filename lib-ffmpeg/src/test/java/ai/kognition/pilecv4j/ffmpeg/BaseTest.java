package ai.kognition.pilecv4j.ffmpeg;

import java.io.File;
import java.net.URI;

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

    public final static URI STREAM = new File(
        BaseTest.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

}
