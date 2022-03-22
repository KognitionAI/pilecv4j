package ai.kognition.pilecv4j.ffmpeg;

import java.io.File;
import java.net.URI;

import net.dempsy.util.Functional;
import net.dempsy.vfs.Vfs;

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

    public final static File STREAM_FILE;
    
    static {
    	try (var vfs = new Vfs();) {
            STREAM_FILE = vfs.toFile(new URI("classpath:///test-videos/Libertas-70sec.mp4"));
            if (!STREAM_FILE.exists())
            	throw new RuntimeException();
    	} catch (Exception e) {
    		throw new RuntimeException(e);
    	}
    }

    public final static URI STREAM = STREAM_FILE.toURI();
}
