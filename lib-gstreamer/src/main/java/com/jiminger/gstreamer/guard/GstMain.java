package com.jiminger.gstreamer.guard;

import static net.dempsy.util.Functional.uncheck;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.gstreamer.Gst;

import com.jiminger.gstreamer.Branch;
import com.jiminger.gstreamer.BreakoutFilter;

public class GstMain implements AutoCloseable {
    private static int inited = 0;
    private static boolean testMode = false;
    private static final List<AutoCloseable> cleanups = new ArrayList<>();

    public synchronized static void testMode() {
        testMode = true;
    }

    public GstMain() {
        synchronized (GstMain.class) {
            if (inited == 0)
                Gst.init();
            inited++;
        }
        BreakoutFilter.init();
    }

    public GstMain(final Class<?> testClass) {
        this(testClass.getSimpleName());
    }

    public GstMain(final String appName) {
        this(appName, new String[] {});
    }

    public GstMain(final String appName, final String[] args) {
        synchronized (GstMain.class) {
            if (inited == 0)
                Gst.init(appName, args);
            inited++;
        }
    }

    public static void register(final AutoCloseable ac) {
        synchronized (GstMain.class) {
            cleanups.add(ac);
        }
    }

    @Override
    public void close() {
        synchronized (GstMain.class) {
            inited--;
            if (inited == 0) {
                while (cleanups.size() > 0) {
                    uncheck(() -> cleanups.remove(cleanups.size() - 1).close());
                }
            }
            if (!testMode) {
                if (inited == 0)
                    Gst.deinit();
            } else {
                // reset the Branch counter.
                new Branch() {
                    @Override
                    public int hashCode() {
                        super.sequence.set(0);
                        return 0;
                    }
                }.hashCode();
            }
        }
    }

}
