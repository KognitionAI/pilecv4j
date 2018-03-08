package com.jiminger.gstreamer.util;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;

import com.jiminger.gstreamer.CapsBuilder;
import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.GstWrap;

public class FrameCatcher {
    public final AppSink sink;
    public final List<Frame> frames = new LinkedList<>();

    public static class Frame {
        public final byte[] data;
        public final int w;
        public final int h;

        public Frame(final byte[] data, final int w, final int h) {
            super();
            this.data = data;
            this.w = w;
            this.h = h;
        }
    }

    private final Function<NewSample, FlowReturn> handler = elem -> {
        try (final GstWrap<Sample> sample = new GstWrap<>(elem.pull())) {
            final Structure capsStruct = sample.obj.getCaps().getStructure(0);
            final int w = capsStruct.getInteger("width");
            final int h = capsStruct.getInteger("height");
            try (final BufferWrap buffer = new BufferWrap(sample.obj.getBuffer());) {
                final ByteBuffer bb = buffer.map(false);
                final byte[] frameData = new byte[bb.remaining()];
                bb.get(frameData);
                frames.add(new Frame(frameData, w, h));
            }
        }
        return FlowReturn.OK;
    };

    public FrameCatcher(final String name) {
        sink = (AppSink) ElementFactory.make("appsink", name);
        sink.setCaps(new CapsBuilder("video/x-raw")
                .addFormatConsideringEndian()
                .build());
        sink.set("emit-signals", true);

        // sink.connect(NewSample.preroller(handler));
        sink.connect(NewSample.sampler(handler));
    }

}
