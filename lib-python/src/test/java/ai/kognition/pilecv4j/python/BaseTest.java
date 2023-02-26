package ai.kognition.pilecv4j.python;

import static ai.kognition.pilecv4j.python.UtilsForTesting.translateClasspath;

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

    public static final ParamBlock fakeParams = ParamBlock.builder()
        .arg("int", 1)
        .arg("float", 1.0)
        .arg("string", "hello");

    public static final String TEST_IMAGE = translateClasspath("test-data/people.jpeg");

}
