package com.jiminger.gstreamer;

import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.util.GstUtils;
import com.jiminger.image.CvRaster;

public class TestBreakoutFilter {

    @Test
    public void testBreakoutFilterLoad() throws Exception {
        try (final GstMain main = new GstMain();) {
            BreakoutFilter.init();
            final BreakoutFilter breakout = (BreakoutFilter) ElementFactory.make("breakout", "breakout");
            breakout.connect((BreakoutFilter.NEW_SAMPLE) elem -> {
                try (final BufferWrap buffer = elem.getCurrentBuffer();) {
                    if (buffer.obj.isWritable()) {
                        final ByteBuffer bb = buffer.map(true);
                        final int w = elem.getCurrentFrameWidth();
                        final int h = elem.getCurrentFrameHeight();
                        try (final CvRaster r = CvRaster.createManaged(h, w, CvType.CV_8UC3);) {
                            final ByteBuffer retData = r.underlying;
                            retData.put(bb);
                            bb.rewind();
                            retData.flip();
                            r.matAp(mat -> {
                                final int thickness = (int) (0.003d * mat.width());
                                Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                                        new Point(0.1 * mat.width(), 0.1 * mat.height()),
                                        new Scalar(0xff, 0xff, 0xff), thickness);
                            });
                            retData.rewind();
                            bb.put(retData);
                        }
                    } else
                        System.out.println("Not Writable");
                }
            });

            final Pipeline pipe = new BinBuilder()
                    .delayed(new URIDecodeBin("src")).with("uri", "file:///home/jim/Videos/Dave Smith Libertas (2017).mp4")
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw").addFormatConsideringEndian().buildString())
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
