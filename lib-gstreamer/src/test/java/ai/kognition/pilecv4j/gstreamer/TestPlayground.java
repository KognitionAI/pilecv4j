package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import net.dempsy.util.MutableInt;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

@Ignore
public class TestPlayground {
    // static {
    // GstUtils.testMode();
    // }

    @Test
    public void test() throws Exception {
        final MutableInt count = new MutableInt(0);

        try(GstScope scope = new GstScope();

            @SuppressWarnings("resource")
            final BreakoutFilter breakout = new BreakoutFilter("breakout")
                .filter(mac -> {
                    count.val++;
                    if((count.val & 0xf) == 0)
                        System.out.println("HERE at " + count.val + " frames");
                    final VideoFrame mat = mac;
                    final int thickness = 5;
                    Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                        new Point(0.1 * mat.width(), 0.1 * mat.height()),
                        new Scalar(0xff, 0xff, 0xff), thickness);

                });

            final Pipeline pipe =

                new BinManager()
                    .stopOnEndOfStream()
                    .delayed("rtspsrc").with("location", "rtsp://admin:gregormendel1@172.16.2.11:554/").with("latency", 0)
                    .delayed("decodebin")
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .buildString())
                    .add(breakout)
                    // .make("videoconvert")
                    // .make("xvimagesink")
                    .make("fakesink")
                    .buildPipeline();) {

            pipe.play();

            Thread.sleep(500);
            GstUtils.printDetails(pipe);

            Gst.main();
        }
    }
}
