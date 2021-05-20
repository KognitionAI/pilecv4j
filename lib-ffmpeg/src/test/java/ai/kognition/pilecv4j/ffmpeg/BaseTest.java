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

    public final static File STREAM_FILE = new File(BaseTest.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile());
    // public final static File STREAM_FILE = new File("/tmp/test-videos/heron8-clip.mp4");

    public final static URI STREAM = STREAM_FILE.toURI();
    // public final static URI STREAM = uncheck(() -> new URI("rtsp://admin:gregormendel1@172.16.2.11:554/"));
}
