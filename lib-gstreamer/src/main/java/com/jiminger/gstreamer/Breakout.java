package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.util.GstUtils.dispose;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Format;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.guard.BufferWrap;
import com.jiminger.gstreamer.guard.GstWrap;
import com.jiminger.gstreamer.util.NewSample;

public class Breakout {
    private static final Logger LOGGER = LoggerFactory.getLogger(Breakout.class);

    public final AppSink input;
    public final AppSrc output;
    private ProcessFrame frameProcessor = null;
    private final AtomicBoolean needsData = new AtomicBoolean(false);
    private OutputMode mode = OutputMode.BLOCKING;

    public enum OutputMode {
        BLOCKING,
        MANAGED
    }

    @FunctionalInterface
    public static interface ProcessFrame {
        public Buffer processFrame(Sample sample);
    }

    public Breakout(final String inputSinkName, final String outputSrcName) {
        input = (AppSink) ElementFactory.make("appsink", inputSinkName);
        output = (AppSrc) ElementFactory.make("appsrc", outputSrcName);
    }

    public Breakout live(final boolean live) {
        output.setLive(live);
        return this;
    }

    public Breakout latency(final int latencyMin, final int latencyMax) {
        output.setLatency(latencyMin, latencyMax);
        return this;
    }

    public Breakout setTimestamp(final boolean setTimestamp) {
        output.setTimestamp(setTimestamp);
        return this;
    }

    public Breakout format(final Format format) {
        output.setFormat(format);
        return this;
    }

    public Breakout type(final AppSrc.Type type) {
        output.setStreamType(type);
        return this;
    }

    public Breakout connect(final AppSrc.ENOUGH_DATA enoughDataCallback) {
        output.connect(enoughDataCallback);
        return this;
    }

    public Breakout connect(final AppSrc.NEED_DATA needDataCallback) {
        output.connect(needDataCallback);
        return this;
    }

    public Breakout setInputCaps(final Caps caps) {
        input.setCaps(caps);
        return this;
    }

    public Breakout frameHandler(final ProcessFrame frameProcessor) {
        this.frameProcessor = frameProcessor;
        return this;
    }

    public Breakout outputMode(final OutputMode mode) {
        this.mode = mode;
        return this;
    }

    public static class SlowFrameProcessor implements ProcessFrame {
        final Thread thread;
        final AtomicReference<Sample> to = new AtomicReference<>(null);
        final AtomicReference<Buffer> result = new AtomicReference<>(null);
        final AtomicReference<Buffer> current = new AtomicReference<>(null);

        public SlowFrameProcessor(final ProcessFrame processor) {
            thread = new Thread(() -> {
                while (true) {
                    Sample frame = to.getAndSet(null);
                    while (frame == null) {
                        Thread.yield();
                        frame = to.getAndSet(null);
                    }
                    try (GstWrap<Sample> sample = new GstWrap<>(frame)) {
                        result.set(processor.processFrame(frame));
                    }
                }
            });

            thread.setDaemon(true);
            thread.start();
        }

        boolean fromSlow = false;

        @Override
        public Buffer processFrame(final Sample sample) {
            dispose(to.getAndSet(sample));

            try (BufferWrap res = new BufferWrap(result.getAndSet(null))) {
                if (res.obj != null)
                    dispose(current.getAndSet(res.disown()));

                final Buffer cur = current.get();
                if (cur == null) {
                    if (!fromSlow && LOGGER.isDebugEnabled()) {
                        fromSlow = true;
                        LOGGER.debug("Playing unprocessed stream");
                    }
                    return sample.getBuffer().copy();
                } else {
                    if (fromSlow && LOGGER.isDebugEnabled()) {
                        fromSlow = false;
                        LOGGER.debug("Playing processed stream");
                    }
                    return cur.copy();
                }
            }
        }
    }

    // private final ProcessFrame passthroughFrameProcessor = s -> {
    // try (GstWrap<Sample> sample = new GstWrap<>(s); BufferWrap buf = new BufferWrap(sample.obj.getBuffer())) {
    // final ByteBuffer bb = buf.map(false);
    // try (final BufferWrap ret = new BufferWrap(new Buffer(bb.remaining()));) {
    // LOGGER.trace("breakout got frame {}", (int) bb.get(0));
    // final ByteBuffer bb2 = ret.map(true);
    // bb2.put(bb);
    // return ret.disown();
    // }
    // }
    // };

    private Function<NewSample, FlowReturn> blocking(final ProcessFrame processor) {
        return elem -> {
            LOGGER.trace("New blocking {} from {}", (elem.preroll ? "preroll" : "sample"), elem.elem.getName());
            final Sample sample = elem.pull();
            final Buffer b = processor.processFrame(sample);
            if (b != null)
                output.pushBuffer(b);
            return FlowReturn.OK;
        };
    }

    private Function<NewSample, FlowReturn> managed(final ProcessFrame processor) {
        return new ManagedAppSinkListener(needsData, processor);
    }

    public Bin build() {
        Function<NewSample, FlowReturn> mainCallback = null;
        final Function<NewSample, FlowReturn> prerollCallback = null;

        input.set("emit-signals", true);

        switch (mode) {
            case BLOCKING:
                mainCallback = blocking(frameProcessor);
                output.set("emit-signals", false);
                output.set("block", true);
                break;
            case MANAGED:
                mainCallback = managed(frameProcessor);
                output.set("emit-signals", true);
                output.connect((AppSrc.NEED_DATA) (elem, size) -> {
                    LOGGER.trace("{} needs {} bytes of data", elem.getName(), size);
                    needsData.set(true);
                });
                output.connect((AppSrc.ENOUGH_DATA) (elem) -> {
                    LOGGER.trace("{} has enough data", elem.getName());
                    needsData.set(false);
                });
                break;
        }

        final CapsTransfer ct = new CapsTransfer(prerollCallback == null ? mainCallback : prerollCallback);
        input.connect(ct);
        input.connect(NewSample.sampler(mainCallback));
        return new BinBuilder()
                .add(input)
                .add(output)
                .buildBin();
    }

    private class CapsTransfer implements AppSink.NEW_PREROLL, Function<NewSample, FlowReturn> {
        final Function<NewSample, FlowReturn> successor;

        private CapsTransfer(final Function<NewSample, FlowReturn> successor) {
            this.successor = successor;
        }

        @Override
        public FlowReturn apply(final NewSample elem) {
            LOGGER.trace("Caps transfer during {} on {}", (elem.preroll ? "preroll" : "sample"), elem.elem.getName());
            final Pad sinkPad = input.getSinkPads().get(0);
            final Caps caps = sinkPad.getNegotiatedCaps();
            output.setCaps(caps);
            // input.disconnect(this);
            // input.disconnect((AppSink.NEW_SAMPLE) this);
            // input.connect(NewSample.preroller(successor));
            // input.connect(NewSample.sampler(successor));
            final FlowReturn ret = successor.apply(elem);
            // LOGGER.trace("Caps transfer PREROLL returning {}", ret);
            return ret;
        }

        @Override
        public FlowReturn newPreroll(final AppSink elem) {
            return apply(new NewSample(elem, true));
        }
    }

    private class ManagedAppSinkListener implements Function<NewSample, FlowReturn> {
        private final AtomicBoolean needsData;
        private final ProcessFrame processor;
        boolean blockedAck = false;

        private ManagedAppSinkListener(final AtomicBoolean needsData, final ProcessFrame processor) {
            this.needsData = needsData;
            this.processor = processor;
        }

        @Override
        public FlowReturn apply(final NewSample elem) {
            LOGGER.trace("New managed {} from {}", (elem.preroll ? "preroll" : "sample"), elem.elem.getName());
            final Sample sample = elem.pull();
            final Buffer b = processor.processFrame(sample);
            if (b != null) {
                while (!needsData.get()) {
                    if (!blockedAck) {
                        blockedAck = true;
                        LOGGER.trace("{} is blocked waiting for a NEED_DATA", elem.elem.getName());
                    }
                    Thread.yield();
                }
                if (blockedAck)
                    LOGGER.trace("{} is no longer blocked.", elem.elem.getName());
                blockedAck = false;
                output.pushBuffer(b);
            }
            return FlowReturn.OK;
        }
    }
}
