package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

@Ignore
public class TestPlayground {
    static {
        GstUtils.testMode();
    }

    @Test
    public void test() throws Exception {

        try(final GstScope main = new GstScope();) {
            final BreakoutFilter breakout = (BreakoutFilter)ElementFactory.make("breakout", "breakout");
            breakout.filter(mac -> {
                final VideoFrame mat = mac;
                final int thickness = 5;
                Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                    new Point(0.1 * mat.width(), 0.1 * mat.height()),
                    new Scalar(0xff, 0xff, 0xff), thickness);
            });

            final Pipeline pipe =

                new BinManager()
                    .delayed("rtspsrc").with("location", "rtsp://admin:gregormendel1@172.16.3.11:554/").with("latency", 0)
                    .delayed("decodebin")
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .buildString())
                    .add(breakout)
                    .make("videoconvert")
                    .make("xvimagesink")
                    .buildPipeline();

            pipe.play();

            // Thread.sleep(500);
            // GstUtils.printDetails(pipe);

            Gst.main();
        }
    }
}
