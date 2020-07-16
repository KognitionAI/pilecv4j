package ai.kognition.pilecv4j.gstreamer;

import static ai.kognition.pilecv4j.gstreamer.util.GstUtils.printDetails;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintStream;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

public class TestBranchWithMultiqueue extends BaseTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testBranch() throws Exception {
        try(GstScope scope = new GstScope();
            final Caps capsw = new CapsBuilder("video/x-raw")
                .addFormatConsideringEndian()
                .add("width", "640")
                .add("height", "480")
                .build();
            final FrameCatcher fc1 = new FrameCatcher("fc1");
            final FrameCatcher fc2 = new FrameCatcher("fc2");

            final Pipeline pipe = new BinManager()
                .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
                .make("videoscale")
                .make("videoconvert")
                .caps(capsw)
                .teeWithMultiqueue(new Branch("b1_")
                    .make("fakesink"),
                    new Branch("b2_")
                        .add(fc1.disown()),
                    new Branch("b3_")
                        .add(fc2.disown()))
                .buildPipeline();) {

            pipe.play();
            Thread.sleep(1000);
            final File file = folder.newFile("pipeline.txt");
            try(final PrintStream ps = new PrintStream(file)) {
                printDetails(pipe, ps);
            }
            assertTrue(file.exists());
            pipe.stop();
            assertTrue(poll(o -> !pipe.isPlaying()));
            assertTrue(fc1.frames.size() > 10);
            assertEquals(fc1.frames.size(), fc2.frames.size());
        }
    }
}
