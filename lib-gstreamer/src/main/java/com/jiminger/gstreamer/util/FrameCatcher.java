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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.CapsBuilder;
import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.GstWrap;

public class FrameCatcher implements AutoCloseable {
    private static Logger LOGGER = LoggerFactory.getLogger(FrameCatcher.class);
    private AppSink sink;
    public final List<Frame> frames = new LinkedList<>();
    private boolean handledPreroll = false;

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

    public FrameCatcher(final String name) {
        sink = (AppSink) ElementFactory.make("appsink", name);
        sink.setCaps(new CapsBuilder("video/x-raw")
                .addFormatConsideringEndian()
                .build());
        sink.set("emit-signals", true);

        sink.connect(NewSample.preroller(handler));
        sink.connect(NewSample.sampler(handler));
    }

    public AppSink disown() {
        final AppSink ret = sink;
        sink = null;
        return ret;
    }

    @Override
    public void close() {
        if (sink != null) {
            sink.dispose();
            disown();
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

                if (!elem.preroll || handledPreroll)
                    frames.add(new Frame(frameData, w, h));

                if (elem.preroll) {
                    if (FrameEmitter.HACK_FRAME)
                        LOGGER.trace("byte0 (" + frameData[0] + ")");
                    handledPreroll = true;
                } else if (FrameEmitter.HACK_FRAME)
                    LOGGER.trace("byte0 " + frameData[0]);
            }
        }
        return FlowReturn.OK;
    };

    private static class NewSample {
        public final AppSink elem;
        public final boolean preroll;

        public static AppSink.NEW_PREROLL preroller(final Function<NewSample, FlowReturn> func) {
            return e -> func.apply(new NewSample(e, true));
        }

        public static AppSink.NEW_SAMPLE sampler(final Function<NewSample, FlowReturn> func) {
            return e -> func.apply(new NewSample(e, false));
        }

        public NewSample(final AppSink elem, final boolean preroll) {
            this.elem = elem;
            this.preroll = preroll;
        }

        public Sample pull() {
            return preroll ? elem.pullPreroll() : elem.pullSample();
        }
    }

}
