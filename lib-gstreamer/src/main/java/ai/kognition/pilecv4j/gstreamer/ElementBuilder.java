package ai.kognition.pilecv4j.gstreamer;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;

import ai.kognition.pilecv4j.gstreamer.guard.GstScope;

/**
 *  This class can be used to build an {@link Element} using a builder pattern.
 */
public class ElementBuilder {
    private Element currentElement;

    public ElementBuilder(final String elementFactoryName, final String elementName) {
        make(elementFactoryName, elementName);
    }

    public ElementBuilder(final String elementFactoryName) {
        make(elementFactoryName);
    }

    public ElementBuilder() {}

    /**
     * Add an element that has static pads. 
     */
    public ElementBuilder make(final String element) {
        return make(element, nextName(element));
    }

    /**
     * Add an element that has static pads. 
     */
    public ElementBuilder make(final String element, final String name) {
        currentElement = ElementFactory.make(element, name);
        return this;
    }

    /**
     * Set a property on the most recently added element or if no element
     * has been added yet, on the Bin itself.
     */
    public ElementBuilder with(final String name, final Object value) {
        currentElement.set(name, value);
        return this;
    }

    public Element build() {
        return currentElement;
    }

    public Element build(final GstScope scope) {
        return scope.manage(currentElement);
    }

    static String nextName(final String basename) {
        return basename + Branch.sequence.getAndIncrement();
    }
}