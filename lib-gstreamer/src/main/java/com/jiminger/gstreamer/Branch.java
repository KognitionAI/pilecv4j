package com.jiminger.gstreamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  This class can be used to build either a {@link Bin} or a {@link Pipeline} using a builder patter.
 *  It encapsulates many of the boilerplate manipulations including naming, adding and linking elements.
 */
public class Branch {
    private static final Logger LOGGER = LoggerFactory.getLogger(Branch.class);

    private final List<List<Element>> elements = new ArrayList<>();
    private List<Element> currentList = new ArrayList<>();
    private Element currentElement;
    private final String prefix;
    Element first = null;
    Element last = null;

    Branch source;
    final List<Branch> sinks = new ArrayList<>();

    public Branch(final String prefix) {
        this.prefix = prefix;
        elements.add(currentList);
    }

    public Branch() {
        this("");
    }

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public Branch delayed(final Element element) {
        add(element);
        currentList = new ArrayList<>();
        elements.add(currentList);
        return this;
    }

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public Branch delayed(final String element) {
        return delayed(element, nextName(element));
    }

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public Branch delayed(final String element, final String name) {
        return delayed(ElementFactory.make(element, name));
    }

    /**
     * Add an element that has static pads. 
     */
    public Branch make(final String element) {
        return make(element, nextName(element));
    }

    /**
     * Add an element that has static pads. 
     */
    public Branch make(final String element, final String name) {
        return add(ElementFactory.make(element, name));
    }

    /**
     * Add a caps filter statement. Also see {@link CapsBuilder}.
     */
    public Branch caps(final String caps) {
        return caps(new Caps(caps));
    }

    /**
     * Add a caps filter statement. Also see {@link CapsBuilder}.
     */
    public Branch caps(final Caps caps) {
        final Element ret = ElementFactory.make("capsfilter", nextName("capsfilter"));
        ret.setCaps(caps);
        return add(ret);
    }

    /**
     * Add an element that has static pads. 
     */
    public Branch add(final Element e) {
        currentList.add(e);
        if (first == null)
            first = e;
        last = e;
        currentElement = e;
        return this;
    }

    /**
     * Set a property on the most recently added element.
     */
    public Branch with(final String name, final Object value) {
        currentElement.set(name, value);
        return this;
    }

    void connectDownstreams(final Branch... downstream) {
        Arrays.stream(downstream).forEach(b -> {
            b.source = this;
            this.sinks.add(b);
        });
    }

    /**
     * Convert the current builder to a {@link Bin}. This will also manage
     * the GhostPads. Currently it assumes there's a single src pad and
     * a single sink pad at the ends of the chain of elements that have been
     * added.
     */
    public Bin buildBin() {
        final Bin bin = new Bin();
        addAllTo(bin);
        linkAll(bin);
        List<Pad> pads = first.getSinkPads();
        for (final Pad pad : pads)
            bin.addPad(new GhostPad("GstSink:" + pad.getName(), pad));
        pads = last.getSrcPads();
        for (final Pad pad : pads)
            bin.addPad(new GhostPad("GstSrc:" + pad.getName(), pad));
        return bin;
    }

    void addAllTo(final Bin bin) {
        for (final List<Element> c : elements) {
            for (final Element e : c)
                bin.add(e);
        }
    }

    void linkAll(final Bin bin) {
        // each break represents a delayed
        Element last = null;
        for (final List<Element> c : elements) {
            Element prev = null;
            for (final Element e : c) {
                if (last != null) {
                    last.connect(getDelayedCallback(e));
                    last = null;
                }
                if (prev != null) {
                    prev.link(e);
                }
                prev = e;
            }
            last = prev;
        }
    }

    void disposeAll() {
        for (int i = elements.size() - 1; i >= 0; i--) {
            final List<Element> cur = elements.get(i);
            for (int j = cur.size() - 1; j >= 0; j--) {
                final Element ce = cur.get(j);
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("disposing {} with a ref count of {}", ce, ce.getRefCount());
                ce.dispose();
            }
        }
    }

    protected final AtomicInteger sequence = new AtomicInteger(0);

    private String nextName(final String basename) {
        return prefix + basename + sequence.getAndIncrement();
    }

    private Element.PAD_ADDED getDelayedCallback(final Element next) {
        return (Element.PAD_ADDED) (element, pad) -> {
            if (pad.isLinked())
                return;
            pad.link(next.getSinkPads().get(0));
            // final Caps caps = pad.getCaps();
            // if (caps.size() > 0) {
            // final Structure struct = caps.getStructure(0);
            // final String capName = struct.getName();
            // if ("video/x-raw".equalsIgnoreCase(capName))
            // pad.link(next.getSinkPads().get(0));
            // else if ("video".equalsIgnoreCase(struct.getString("media")))
            // pad.link(next.getSinkPads().get(0));
            // }
        };
    }
}