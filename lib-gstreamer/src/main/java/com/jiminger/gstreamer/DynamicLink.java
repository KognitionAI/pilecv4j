package com.jiminger.gstreamer;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;

public interface DynamicLink {
    public void padAdded(Element src, Pad srcPadAdded, Element sink);
}