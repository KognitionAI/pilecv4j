package com.jiminger.gstreamer;

import java.io.File;
import java.net.URI;

import com.jiminger.gstreamer.guard.GstMain;

public class BaseTest {
    static {
        GstMain.testMode();
    }

    public final static URI STREAM = new File(
            TestFrameCatcherUnusedCleansUp.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

}
