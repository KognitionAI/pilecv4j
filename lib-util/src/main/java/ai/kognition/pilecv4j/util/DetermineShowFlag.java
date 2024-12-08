package ai.kognition.pilecv4j.util;

/**
 * A simple utility for consistently determining whether or not to show
 * results during testing
 */
public class DetermineShowFlag {

    public final static boolean SHOW; /// can only set this to true when building on a machine with a display

    static {
        final String sysOpSHOW = System.getProperty("pilecv4j.SHOW");
        final boolean sysOpSet = sysOpSHOW != null;
        boolean show = ("".equals(sysOpSHOW) || Boolean.parseBoolean(sysOpSHOW));
        if(!sysOpSet)
            show = Boolean.parseBoolean(System.getenv("PILECV4J_SHOW"));
        SHOW = show;
    }

}
