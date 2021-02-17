package ai.kognition.pilecv4j.gstreamer;

import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.freedesktop.gstreamer.Pipeline;
import org.junit.Test;

import ai.kognition.pilecv4j.gstreamer.util.FrameCatcher;

public class TestBuildersEndOnError extends BaseTest {

    @Test
    public void testEndOnError() throws Exception {
        final AtomicBoolean gotHere = new AtomicBoolean(false);
        final AtomicReference<String> errElementName = new AtomicReference<>(null);

        try(GstScope scope = new GstScope();
            final FrameCatcher fc = new FrameCatcher("framecatcher", true);

            final Pipeline pipe = new BinManager()
                .delayed("uridecodebin", "myjunkyderidecodeybin").with("uri", STREAM.toString())
                // intentional mismatch between decodebin output and the FrameCatcher without a videoconvert
                .onError((q, e, y, z, p) -> {
                    gotHere.set(true);
                    errElementName.set(e.getName());
                })
                .add(fc.disown())
                .buildPipeline();) {

            pipe.play();
            assertTrue(poll(o -> gotHere.get()));
            assertEquals("myjunkyderidecodeybin", errElementName.get());
            pipe.stop();
        }
    }
}
