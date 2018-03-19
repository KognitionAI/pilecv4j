package com.jiminger.gstreamer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameCatcher;

public class TestFrameCatcherUnusedCleansUp extends BaseTest {

    @Test
    public void testUnusedCatcher() throws Exception {
        FrameCatcher outside = null;
        try (final GstMain m = new GstMain(TestFrameEmitterAndCatcher.class);) {
            try (final FrameCatcher fc = new FrameCatcher("framecatcher")) {
                outside = fc;
            }
            assertNotNull(outside);
            assertNull(outside.disown());
        }
    }

}
