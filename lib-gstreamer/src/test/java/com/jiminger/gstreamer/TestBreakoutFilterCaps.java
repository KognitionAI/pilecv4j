package com.jiminger.gstreamer;

import org.junit.Test;

import com.jiminger.gstreamer.guard.GstMain;

public class TestBreakoutFilterCaps extends BaseTest {

    @Test(expected = UnsupportedOperationException.class)
    public void testSettingCapsOnBreakoutFilter() throws Exception {
        try (final GstMain m = new GstMain(TestBreakoutPassthrough.class);) {
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
