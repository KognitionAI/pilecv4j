package ai.kognition.pilecv4j.gstreamer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pipeline;
import org.junit.Ignore;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

@Ignore
public class TestPlayground {
    @Test
    public void test() throws Exception {
        // final MutableInt count = new MutableInt(0);

        final AtomicReference<Pipeline> rpipe = new AtomicReference<>();
        final AtomicLong al = new AtomicLong(0);
        try(GstScope scope = new GstScope();

            @SuppressWarnings("resource")
            final BreakoutFilter breakout = new BreakoutFilter("breakout")
                // .streamWatcher(mac -> {
                .filter(mac -> {
                    long cur = al.getAndIncrement();
                    // uncheck(() -> Thread.sleep(100));
                    // count.val++;
                    // // if((count.val & 0xf) == 0)
                    // System.out.println("HERE at " + count.val + " frames");
                    // final VideoFrame mat = mac;
                    // final int thickness = 5;
                    // Imgproc.rectangle(mat, new Point(0.9 * mat.width(), 0.9 * mat.height()),
                    // new Point(0.1 * mat.width(), 0.1 * mat.height()),
                    // new Scalar(0xff, 0xff, 0xff), thickness);
                    //
                    // uncheck(() -> ImageFile.writeImageFile(mac, "/tmp/test.jpg"));
                    if(cur >= 100) {
                        System.out.println("LOOPING:" + rpipe.get().seek(0));
                        al.set(0);
                    }
                });

            final Pipeline pipe =

                new BinManager()
                    // .stopOnEndOfStream()
                    .delayed("uridecodebin").with("uri", "file:///tmp/test-videos/Libertas-people-short.mp4")
                    // .delayed("decodebin")
                    .make("videoconvert")
                    // .caps(new CapsBuilder("video/x-raw")
                    // .addFormatConsideringEndian()
                    // .buildString())
                    // // .add(breakout).with("maxDelayMillis", 100)
                    // .make("videoconvert")
                    .make("xvimagesink")
                    // .with("qos", false)
                    // .make("fakesink")
                    // .with("qos", true)
                    // .with("max-lateness", 10000000)
                    // .with("sync", true)
                    .buildPipeline();) {

            rpipe.set(pipe);
            pipe.getBus().connect(new Bus.EOS() {

                @Override
                public void endOfStream(final GstObject source) {
                    System.out.println("EOS");
                    pipe.seek(0);
                    pipe.play();
                }
            });
            pipe.play();

            Thread.sleep(5000);
            GstUtils.printDetails(pipe);

            Gst.main();
        }
    }
}
