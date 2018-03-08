package com.jiminger.gstreamer.guard;

import org.freedesktop.gstreamer.Element;

public class ElementWrap<T extends Element> implements AutoCloseable {
    public final T element;

    public ElementWrap(final T element) {
        this.element = element;
    }

    @Override
    public void close() {
        this.element.stop();
    }
}
