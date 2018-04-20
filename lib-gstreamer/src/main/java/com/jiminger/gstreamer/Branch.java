package com.jiminger.gstreamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadDirection;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  This class can be used to build either a {@link Bin} or a {@link Pipeline} using a builder patter.
 *  It encapsulates many of the boilerplate manipulations including naming, adding and linking elements.
 */
public class Branch {
    private static final Logger LOGGER = LoggerFactory.getLogger(Branch.class);
    final static AtomicInteger sequence = new AtomicInteger(0);

    private final List<List<Element>> elements = new ArrayList<>();
    private List<Element> currentList = new ArrayList<>();
    private final Map<String, Object> binprops = new HashMap<>();

    private Element currentElement;

    private final String prefix;

    private String binFactoryName = null;
    private Pad end = null;

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
     * Set the type of the Bin that wraps this branch. For example, you could use "rtpbin"
     */
    public Branch elementFactory(final String elementFactoryName) {
        this.binFactoryName = elementFactoryName;
        return this;
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
     * Set a property on the most recently added element or if no element
     * has been added yet, on the Bin itself.
     */
    public Branch with(final String name, final Object value) {
        if (currentElement == null)
            binprops.put(name, value);
        else
            currentElement.set(name, value);
        return this;
    }

    public Branch linkEnd(final Pad pad) {
        // should be a sink pad
        if (pad.getDirection() != PadDirection.SINK)
            throw new IllegalArgumentException("Cannot terminate branch " + prefix + " using a " + pad.getDirection() + " direction pad.");
        this.end = pad;
        return this;
    }

    /**
     * Convert the current builder to a {@link Bin}.  if requested, this 
     * will also add GhostPads for all sink pads at the beginning of the
     * chain and all src pads at the end of the chain. 
     */
    public Bin buildBin(final String binName, final boolean ghostPads) {
        final Bin bin = binFactoryName == null || binFactoryName.isEmpty() ? (binName == null ? new Bin() : new Bin(binName))
                : (Bin) ElementFactory.make(binFactoryName, binName == null ? nextName("bin") : binName);

        if (binprops.size() > 0)
            binprops.entrySet().stream()
                    .forEach(e -> bin.set(e.getKey(), e.getValue()));

        addAllTo(bin);
        linkAll();

        boolean endSet = false;
        if (ghostPads) {
            if (first != null) {
                final List<Pad> pads = first.getSinkPads();
                for (final Pad pad : pads)
                    bin.addPad(new GhostPad("GstSink:" + pad.getName(), pad));
            }

            if (last != null) {
                final List<Pad> pads = last.getSrcPads();
                for (final Pad pad : pads) {
                    final GhostPad gpad = new GhostPad("GstSrc:" + pad.getName(), pad);
                    if (!endSet && end != null) {
                        if (pads.size() > 1)
                            LOGGER.warn("explicit branch terminating pad set but the last element has multiple src pads. Using the first pad "
                                    + gpad.getName());
                        gpad.link(end);
                        endSet = true;
                    }
                    bin.addPad(gpad);
                }
            }
        }

        if (last != null && end != null && !endSet) {
            final List<Pad> pads = last.getSrcPads();
            if (pads.size() > 0) {
                final Pad pad = pads.get(0);
                if (pads.size() > 1)
                    LOGGER.warn("explicit branch terminating pad set but the last element has multiple src pads. Using the first pad "
                            + pad.getName());
                pad.link(end);
            }
        }

        return bin;
    }

    public void addAllTo(final Bin bin) {
        for (final List<Element> c : elements) {
            for (final Element e : c)
                bin.add(e);
        }
    }

    public List<Element> linkAll() {
        final List<Element> ret = new ArrayList<>();

        // each break represents a delayed
        Element last = null;
        for (final List<Element> c : elements) {
            Element prev = null;

            for (final Element e : c) {
                ret.add(e);

                if (last != null) {
                    last.connect(getDelayedCallback(e));
                    last = null;
                }

                if (prev != null)
                    prev.link(e);

                prev = e;
            }
            last = prev;
        }

        return ret;
    }

    void connectDownstreams(final Branch... downstream) {
        Arrays.stream(downstream).forEach(b -> {
            b.source = this;
            this.sinks.add(b);
        });
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

    protected void hackResetSequence() {
        sequence.set(0);
    }

    private String nextName(final String basename) {
        return prefix + basename + sequence.getAndIncrement();
    }

    private Element.PAD_ADDED getDelayedCallback(final Element next) {
        return (Element.PAD_ADDED) (element, pad) -> {
            if (pad.isLinked())
                return;
            pad.link(next.getSinkPads().get(0));
        };
    }
}