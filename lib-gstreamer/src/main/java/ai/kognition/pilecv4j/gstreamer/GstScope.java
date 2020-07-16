package ai.kognition.pilecv4j.gstreamer;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

public class GstScope implements AutoCloseable {
    public GstScope() {
        this(GstUtils.DEFAULT_APP_NAME, new String[0]);
    }

    public GstScope(final Class<?> testClass) {
        this(testClass.getSimpleName());
    }

    public GstScope(final String appName) {
        this(appName, new String[] {});
    }

    public GstScope(final String appName, final String[] args) {
        GstUtils.safeGstInit(appName, args);
        BreakoutFilter.initFromScope();
    }

    @Override
    public void close() {
        synchronized(GstScope.class) {
            GstUtils.safeGstDeinit();
        }
    }

}
