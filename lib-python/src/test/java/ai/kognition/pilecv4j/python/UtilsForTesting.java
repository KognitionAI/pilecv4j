package ai.kognition.pilecv4j.python;

import java.io.File;

public class UtilsForTesting {

    public static String translateClasspath(final String classpathPath) {
        return new File(UtilsForTesting.class.getClassLoader().getResource(classpathPath).getFile()).getAbsolutePath();
    }
}
