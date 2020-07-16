package ai.kognition.pilecv4j.gstreamer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

public class TestFrameCatcherUnusedCleansUp extends BaseTest {

    @Test
    public void testUnusedCatcher() throws Exception {
        try(GstScope scope = new GstScope();) {
            FrameCatcher outside = null;
            try(final FrameCatcher fc = new FrameCatcher("framecatcher")) {
                outside = fc;
            }
            assertNotNull(outside);
            assertNull(outside.disown());
        }
    }
}
