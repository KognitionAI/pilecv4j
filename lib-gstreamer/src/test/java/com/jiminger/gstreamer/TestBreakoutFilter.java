package com.jiminger.gstreamer;

import java.io.File;
import java.net.URI;

import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.FrameEmitter;
import com.jiminger.gstreamer.util.GstUtils;
import com.jiminger.image.CvRaster;

public class TestBreakoutFilter {

    final static URI STREAM = new File(
            TestBuilders.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

    @Test
    public void testBreakoutFilterLoad() throws Exception {
        try (final GstMain main = new GstMain();) {
            BreakoutFilter.init();
            final BreakoutFilter breakout = (BreakoutFilter) ElementFactory.make("breakout", "breakout");

            breakout.connect((BreakoutFilter.NEW_SAMPLE) elem -> {
                try (final BufferWrap buffer = elem.getCurrentBuffer();
                        CvRaster r = buffer.mapToRaster(elem.getCurrentFrameHeight(), elem.getCurrentFrameWidth(), CvType.CV_8UC3, true)) {
                    r.matAp(mat -> {
                        final int thickness = (int) (0.003d * mat.width());
                        Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                                new Point(0.1 * mat.width(), 0.1 * mat.height()),
                                new Scalar(0xff, 0xff, 0xff), thickness);
                    });
                    return FlowReturn.OK;
                }
            });

            final Pipeline pipe = new BinBuilder()
                    .add(new FrameEmitter(STREAM.toString(), 40).element)
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw")
                            .addFormatConsideringEndian()
                            .buildString())
                    .make("queue")
                    .add(breakout)
                    .make("videoconvert")
                    .make("xvimagesink")
                    .buildPipeline();

            pipe.play();
            GstUtils.instrument(pipe);
            Gst.main();
        }
    }

}
