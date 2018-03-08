package com.jiminger.gstreamer.util;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.freedesktop.gstreamer.elements.URIDecodeBin;
import org.freedesktop.gstreamer.event.EOSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.BinBuilder;
import com.jiminger.gstreamer.Breakout;
import com.jiminger.gstreamer.CapsBuilder;
import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.GstWrap;

/**
 * This class can be used to source a fixed number of frames for testing purposes.
 */
public class FrameEmitter {
    private final static Logger LOGGER = LoggerFactory.getLogger(FrameEmitter.class);
    private static AtomicInteger sequence = new AtomicInteger(0);
    public final Bin element;
    public final int numFrames;

    private int curFrames = 0;
    private Breakout breakout = null;
    public boolean emitted = false;

    public FrameEmitter(final String sourceUri, final int numFrames) {
        final int seq = sequence.getAndIncrement();
        breakout = new Breakout("emitter" + seq + "-sink", "emitter" + seq + "-src")
                .frameHandler(s -> {
                    try (GstWrap<Sample> sample = new GstWrap<>(s);
                            BufferWrap buf = new BufferWrap(sample.obj.getBuffer())) {
                        final ByteBuffer bb = buf.map(false);
                        try (final BufferWrap ret = new BufferWrap(new Buffer(bb.remaining()));) {
                            final ByteBuffer bb2 = ret.map(true);
                            bb2.put(bb);
                            bb2.put(0, (byte) curFrames);
                            if (isDone()) {
                                if (!emitted) {
                                    breakout.output.sendEvent(new EOSEvent());
                                    emitted = true;
                                }
                                return null;
                            } else {
                                LOGGER.trace("emitter emitting frame {}", curFrames);
                                curFrames++;
                            }
                            return ret.disown();
                        }
                    }
                })
                .live(true)
                .setTimestamp(true)
                .format(Format.TIME)
                .type(AppSrc.Type.STREAM)
                .setInputCaps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .build());

        element = new BinBuilder()
                .delayed(new URIDecodeBin("source")).with("uri", sourceUri)
                .make("videoconvert")
                .caps(new CapsBuilder("video/x-raw")
                        .addFormatConsideringEndian()
                        .buildString())
                .add(breakout.build())
                .buildBin();

        this.numFrames = numFrames;
    }

    public boolean isDone() {
        return (curFrames >= numFrames);
    }

}
