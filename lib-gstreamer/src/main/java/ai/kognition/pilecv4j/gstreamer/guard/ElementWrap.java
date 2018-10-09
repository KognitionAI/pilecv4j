package ai.kognition.pilecv4j.gstreamer.guard;

import org.freedesktop.gstreamer.Element;

public class ElementWrap<T extends Element> implements AutoCloseable {
    public final T element;
    private boolean disowned = false;

    public ElementWrap(final T element) {
        this.element = element;
    }

    @Override
    public void close() {
        if (!disowned) {
            this.element.stop();
            while (this.element.isPlaying())
                Thread.yield();
            this.element.dispose();
        }
    }

    public T disown() {
        disowned = true;
        return element;
    }
}
