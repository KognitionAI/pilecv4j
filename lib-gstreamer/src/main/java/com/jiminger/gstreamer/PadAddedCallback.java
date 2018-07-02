package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;

@FunctionalInterface
public interface PadAddedCallback {
    public void padAdded(Element src, Pad srcPadAdded, Element sink);
}