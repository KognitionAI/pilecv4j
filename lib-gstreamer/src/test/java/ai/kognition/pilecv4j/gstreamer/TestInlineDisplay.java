package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;
import ai.kognition.pilecv4j.gstreamer.guard.GstWrap;

// This only works when there's a display
public class TestInlineDisplay extends BaseTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testInlineDisplay() throws Exception {
        if(SHOW) {
            try (final GstScope main = new GstScope();
                final GstWrap<Caps> capsw = new GstWrap<>(new CapsBuilder("video/x-raw")
                    .addFormatConsideringEndian()
                    .add("width", "640")
                    .add("height", "480")
                    .build());) {

                final Pipeline pipe = new BinManager()
                    .scope(main)
                    .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
                    .make("videoscale")
                    .make("videoconvert")
                    .caps(capsw.disown())
                    .add(new BreakoutFilter("inline-display").filter(new InlineDisplay.Builder().build()))
                    .make("fakesink").with("sync", true)
                    .buildPipeline();

                pipe.play();
                Thread.sleep(5000);
            }
        }
    }
}
