package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.GstUtils.dispose;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

public class Breakout {
    private static final Logger LOGGER = LoggerFactory.getLogger(Breakout.class);

    public final AppSink input;
    public final AppSrc output;
    private ProcessFrame frameProcessor = null;
    private final AtomicBoolean needsData = new AtomicBoolean(false);
    private OutputMode mode = OutputMode.BLOCKING;

    AppSink.NEW_SAMPLE testCallback;

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
                    if (!fromSlow) {
                        fromSlow = true;
                        LOGGER.debug("Switch to slow source.");
                    }
                    return sample.getBuffer().copy();
                } else {
                    if (fromSlow) {
                        fromSlow = false;
                        LOGGER.debug("Switch from slow source.");
                    }
                    return cur.copy();
                }
            }
        }

    }

    public Bin build() {
        input.set("emit-signals", true);

        switch (mode) {
            case BLOCKING:
                testCallback = new BlockingAppSinkListener(frameProcessor, output);
                output.set("emit-signals", false);
                break;
            case MANAGED:
                testCallback = new ManagedAppSinkListener(frameProcessor, output, needsData);
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

        final CapsTransfer ct = new CapsTransfer((AppSinkListener) testCallback, output, input);
        input.connect((AppSink.NEW_PREROLL) ct);
        input.connect((AppSink.NEW_SAMPLE) ct);
        return new BinBuilder()
                .add(input)
                .add(output)
                .buildBin();
    }

    private static class CapsTransfer implements AppSink.NEW_SAMPLE, AppSink.NEW_PREROLL {
        final AppSinkListener successor;
        final AppSrc output;
        final AppSink input;

        private CapsTransfer(final AppSinkListener successor, final AppSrc output, final AppSink input) {
            this.successor = successor;
            this.output = output;
            this.input = input;
        }

        @Override
        public FlowReturn newSample(final AppSink elem) {
            return doIt(elem, false);
        }

        @Override
        public FlowReturn newPreroll(final AppSink elem) {
            return doIt(elem, true);
        }

        private FlowReturn doIt(final AppSink elem, final boolean preroll) {
            final Pad sinkPad = input.getSinkPads().get(0);
            final Caps caps = sinkPad.getNegotiatedCaps();
            output.setCaps(caps);
            input.disconnect((AppSink.NEW_PREROLL) this);
            input.disconnect((AppSink.NEW_SAMPLE) this);
            input.connect((AppSink.NEW_PREROLL) successor);
            input.connect((AppSink.NEW_SAMPLE) successor);
            successor.doIt(elem, preroll);
            return FlowReturn.OK;
        }
    }

    private static abstract class AppSinkListener implements AppSink.NEW_SAMPLE, AppSink.NEW_PREROLL {
        protected final ProcessFrame process;
        protected final AppSrc output;

        protected AppSinkListener(final ProcessFrame process, final AppSrc output) {
            this.process = process == null ? s -> {
                return s.getBuffer();
            }
                    : process;
            this.output = output;
        }

        @Override
        public FlowReturn newSample(final AppSink elem) {
            return doIt(elem, false);
        }

        @Override
        public FlowReturn newPreroll(final AppSink elem) {
            return doIt(elem, true);
        }

        public abstract FlowReturn doIt(final AppSink elem, final boolean preroll);
    }

    private static class BlockingAppSinkListener extends AppSinkListener {
        private BlockingAppSinkListener(final ProcessFrame process, final AppSrc output) {
            super(process, output);
        }

        @Override
        public FlowReturn doIt(final AppSink elem, final boolean preroll) {
            final Sample sample = preroll ? elem.pullPreroll() : elem.pullSample();
            final Buffer b = process.processFrame(sample);
            if (b != null)
                output.pushBuffer(b);
            return FlowReturn.OK;
        }
    }

    private static class ManagedAppSinkListener extends AppSinkListener {
        private final AtomicBoolean needsData;
        boolean blockedAck = false;

        private ManagedAppSinkListener(final ProcessFrame process, final AppSrc output, final AtomicBoolean needsData) {
            super(process, output);
            this.needsData = needsData;
        }

        @Override
        public FlowReturn doIt(final AppSink elem, final boolean preroll) {
            final Sample sample = preroll ? elem.pullPreroll() : elem.pullSample();
            final Buffer b = process.processFrame(sample);
            if (b != null) {
                while (!needsData.get()) {
                    if (!blockedAck) {
                        blockedAck = true;
                        LOGGER.trace("{} is blocked waiting for a NEED_DATA", elem.getName());
                    }
                    Thread.yield();
                }
                if (blockedAck)
                    LOGGER.trace("{} is no longer blocked.", elem.getName());
                blockedAck = false;
                output.pushBuffer(b);
            }
            return FlowReturn.OK;
        }
    }
}
