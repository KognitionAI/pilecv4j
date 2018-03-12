package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.util.GstUtils.instrument;
import static net.dempsy.utils.test.ConditionPoll.poll;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.Breakout.OutputMode;
import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.ElementWrap;
import com.jiminger.gstreamer.guard.GstMain;
import com.jiminger.gstreamer.guard.GstWrap;
import com.jiminger.gstreamer.util.FrameCatcher;
import com.jiminger.gstreamer.util.FrameEmitter;

public class TestBreakoutPassthrough {
    private final static Logger LOGGER = LoggerFactory.getLogger(TestBreakoutPassthrough.class);
    final static URI STREAM = new File(
            TestBuilders.class.getClassLoader().getResource("test-videos/Libertas-70sec.mp4").getFile()).toURI();

    @Test
    public void testBreakoutWithPassthrough() throws Exception {
        try (final GstMain m = new GstMain(TestBreakoutPassthrough.class);) {
            final FrameEmitter fe = new FrameEmitter(STREAM.toString(), 30);
            final FrameCatcher fc = new FrameCatcher("framecatcher");

            try (final ElementWrap<Pipeline> ew = new ElementWrap<>(new BinBuilder()
                    .add(fe.element)
                    .make("videoconvert")
                    .caps("video/x-raw")
                    .add(new Breakout("AppSink", "AppSrc")
                            .frameHandler(s -> {
                                try (GstWrap<Sample> sample = new GstWrap<>(s);
                                        BufferWrap buf = new BufferWrap(sample.obj.getBuffer())) {
                                    final ByteBuffer bb = buf.map(false);
                                    try (final BufferWrap ret = new BufferWrap(new Buffer(bb.remaining()));) {
                                        LOGGER.trace("breakout got frame {}", (int) bb.get(0));
                                        final ByteBuffer bb2 = ret.map(true);
                                        bb2.put(bb);
                                        return ret.disown();
                                    }
                                }
                            })
                            .live(true)
                            .outputMode(OutputMode.MANAGED)
                            .setTimestamp(true)
                            .format(Format.TIME)
                            .type(AppSrc.Type.STREAM)
                            .setInputCaps(new CapsBuilder("video/x-raw")
                                    .addFormatConsideringEndian()
                                    .build())
                            .build())
                    .add(fc.sink)
                    .buildPipeline());) {

                final Pipeline pipe = ew.element;
                instrument(pipe);
                pipe.play();
                assertTrue(poll(o -> fe.isDone()));
                Thread.sleep(500);
                pipe.stop();
                assertTrue(poll(o -> !pipe.isPlaying()));
                fc.frames.stream().forEach(f -> {
                    LOGGER.trace("byte0 {}", (int) f.data[0]);
                });

                assertEquals(30, fc.frames.size());
            }
        }
    }
}
