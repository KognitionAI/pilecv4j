package com.jiminger.gstreamer;

import java.io.File;
import java.net.URI;

import com.jiminger.gstreamer.guard.GstScope;

public class BaseTest {
    static {
        GstScope.testMode();
    }

    public final static URI STREAM = new File(
            TestFrameCatcherUnusedCleansUp.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

}
