package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.util.GstUtils.instrument;
import static com.jiminger.gstreamer.util.GstUtils.printDetails;
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

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.guard.GstWrap;
import com.jiminger.gstreamer.util.FrameCatcher;

public class TestBranch extends BaseTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testBranch() throws Exception {
        try (final GstScope main = new GstScope();
                final GstWrap<Caps> capsw = new GstWrap<>(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .add("width", "640")
                        .add("height", "480")
                        .build());
                final FrameCatcher fc1 = new FrameCatcher("fc1");
                final FrameCatcher fc2 = new FrameCatcher("fc2");) {

            final Pipeline pipe = new BinBuilder()
                    .delayed(new URIDecodeBin("source")).with("uri", STREAM.toString())
                    .make("videoscale")
                    .make("videoconvert")
                    .caps(capsw.disown())
                    .tee(new Branch("b1_")
                            .make("queue")
                            .make("fakesink"),
                            new Branch("b2_")
                                    .make("queue")
                                    .add(fc1.disown()),
                            new Branch("b3_")
                                    .make("queue")
                                    .add(fc2.disown()))
                    .buildPipeline(main);

            instrument(pipe);
            pipe.play();
            Thread.sleep(1000);
            final File file = folder.newFile("pipeline.txt");
            try (final PrintStream ps = new PrintStream(file)) {
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
