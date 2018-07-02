package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Element;

@FunctionalInterface
public interface DynamicLink {
    public void link(Element src, Element sink);
}
