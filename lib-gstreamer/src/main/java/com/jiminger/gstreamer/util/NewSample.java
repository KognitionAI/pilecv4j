package com.jiminger.gstreamer.util;

import java.util.function.Function;

import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.elements.AppSink;

public class NewSample {
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