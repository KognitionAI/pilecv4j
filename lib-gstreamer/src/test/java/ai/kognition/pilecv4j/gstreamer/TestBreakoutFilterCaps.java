package ai.kognition.pilecv4j.gstreamer;

import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.BreakoutFilter;
import ai.kognition.pilecv4j.gstreamer.CapsBuilder;
import ai.kognition.pilecv4j.gstreamer.guard.GstScope;

public class TestBreakoutFilterCaps extends BaseTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testSettingCapsOnBreakoutFilter() throws Exception {
        try (final GstScope m = new GstScope(TestBreakoutPassthrough.class);) {
            final BreakoutFilter bf = new BreakoutFilter("testSettingCapsOnBreakoutFilter_filter");
            try {
                bf.setCaps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .build());
            } finally {
                bf.dispose();
            }
        }
    }
}
