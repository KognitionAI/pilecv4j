package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.util.Functional.uncheck;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import net.dempsy.util.MutableInt;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;
import ai.kognition.pilecv4j.image.ImageFile;

@Ignore
public class TestPlayground {
    @Test
    public void test() throws Exception {
        final MutableInt count = new MutableInt(0);

        try(GstScope scope = new GstScope();

            @SuppressWarnings("resource")
            final BreakoutFilter breakout = new BreakoutFilter("breakout")
                // .streamWatcher(mac -> {
                .filter(mac -> {
                    uncheck(() -> Thread.sleep(100));
                    count.val++;
                    // if((count.val & 0xf) == 0)
                    System.out.println("HERE at " + count.val + " frames");
                    final VideoFrame mat = mac;
                    final int thickness = 5;
                    Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                        new Point(0.1 * mat.width(), 0.1 * mat.height()),
                        new Scalar(0xff, 0xff, 0xff), thickness);

                    uncheck(() -> ImageFile.writeImageFile(mac, "/tmp/test.jpg"));
                });

            final Pipeline pipe =

                new BinManager()
                    .stopOnEndOfStream()
                    .delayed("rtspsrc").with("location", "rtsp://admin:gregormendel1@172.16.2.218:554/").with("latency", 0)
                    .delayed("decodebin")
                    .make("videoconvert")
                    .caps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .buildString())
                    .add(breakout)
                    .with("maxDelayMillis", 100)
                    // .make("videoconvert")
                    // .make("xvimagesink")
                    // .with("qos", false)
                    .make("fakesink")
                    // .with("qos", true)
                    // .with("max-lateness", 10000000)
                    // .with("sync", true)
                    .buildPipeline();) {

            pipe.play();

            Thread.sleep(5000);
            GstUtils.printDetails(pipe);

            Gst.main();
        }
    }
}
