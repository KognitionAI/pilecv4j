package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

// This only works when there's a display
public class TestInlineDisplay extends BaseTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testInlineDisplay() throws Exception {
        if(SHOW) {
            try(GstScope scope = new GstScope();
                final Caps capsw = new CapsBuilder("video/x-raw")
                    .addFormatConsideringEndian()
                    .add("width", "640")
                    .add("height", "480")
                    .build();

                BreakoutFilter breakout = new BreakoutFilter("inline-display").watch(new InlineDisplay.Builder().build());) {

                final Pipeline pipe = new BinManager()
                    .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
                    .make("videoscale")
                    .make("videoconvert")
                    .caps(capsw)
                    .add(breakout)
                    .make("fakesink").with("sync", true)
                    .buildPipeline();

                pipe.play();
                Thread.sleep(5000);
            }
        }
    }
}
