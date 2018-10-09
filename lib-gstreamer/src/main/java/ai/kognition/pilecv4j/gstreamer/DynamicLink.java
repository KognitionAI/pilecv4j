package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Element;

@FunctionalInterface
public interface DynamicLink {
    public void link(Element src, Element sink);
}
