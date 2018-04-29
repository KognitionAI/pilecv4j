package com.jiminger.gstreamer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.guard.GstScope;

/**
 *  This class can be used to build either a {@link Bin} or a {@link Pipeline} using a builder pattern.
 *  It encapsulates many of the boilerplate manipulations including naming, adding and linking elements.
 */
public class BinBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinBuilder.class);

    private final Branch current = new Branch();
    private GstScope scope = null;

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public BinBuilder delayed(final Element element) {
        current.delayed(element);
        return this;
    }

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public BinBuilder delayed(final String element) {
        current.delayed(element);
        return this;
    }

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public BinBuilder delayed(final String element, final String name) {
        current.delayed(element, name);
        return this;
    }

    /**
     * If you want to supply your own link logic then you can call this. The most recently
     * added element must be "delayed" or this will throw an IllegalStateException
     */
    public BinBuilder dynamicLink(final DynamicLink linker) {
        current.dynamicLink(linker);
        return this;
    }

    /**
     * Add an element that has static pads. 
     */
    public BinBuilder make(final String element) {
        current.make(element);
        return this;
    }

    /**
     * Add an element that has static pads. 
     */
    public BinBuilder make(final String element, final String name) {
        current.make(element, name);
        return this;
    }

    /**
     * Add a caps filter statement. Also see {@link CapsBuilder}.
     */
    public BinBuilder caps(final String caps) {
        current.caps(caps);
        return this;
    }

    /**
     * Add a caps filter statement. Also see {@link CapsBuilder}.
     */
    public BinBuilder caps(final Caps caps) {
        current.caps(caps);
        return this;
    }

    /**
     * Add an element that has static pads. 
     */
    public BinBuilder add(final Element e) {
        current.add(e);
        return this;
    }

    /**
     * Set a property on the most recently added element.
     */
    public BinBuilder with(final String name, final Object value) {
        current.with(name, value);
        return this;
    }

    /**
     * Split this element chain in multiple directions. 
     */
    public BinBuilder tee(final Branch... branches) {
        current.connectDownstreams(null, branches);
        return this;
    }

    /**
     * Split this element chain in multiple directions using a tee with the
     * given name.
     */
    public BinBuilder tee(final String teeName, final Branch... branches) {
        current.connectDownstreams(teeName, branches);
        return this;
    }

    /**
     * Add the full chain of elements to the given Bin/Pipeline
     */
    public BinBuilder addAllTo(final Bin pipe) {
        addFullChainTo(pipe, current);
        return this;
    }

    /**
     * Internally link all the entire chain if Elements. These must have
     * already been added to a Bin/Pipeline to work correctly.
     */
    public BinBuilder linkAll() {
        Branch.linkFullChainOfBranches(current);
        return this;
    }

    public BinBuilder scope(final GstScope scope) {
        this.scope = scope;
        return this;
    }

    public BinBuilder createGhostPads(final Bin bin) {
        final Element first = current.getFirstElement();

        if (first != null) {
            final List<Pad> pads = first.getSinkPads();
            for (final Pad pad : pads)
                bin.addPad(new GhostPad("GstSink:" + pad.getName(), pad));
        }

        final List<Element> lastList = getEndElements();

        int padnum = 0;
        if (lastList != null) {
            for (final Element last : lastList) {
                final List<Pad> pads = last.getSrcPads();
                for (final Pad pad : pads) {
                    final GhostPad gpad = new GhostPad("GstSrc:" + pad.getName() + padnum, pad);
                    padnum++;
                    bin.addPad(gpad);
                }
            }
        }

        return this;
    }

    /**
     * Convert the current builder to a {@link Pipeline} managed by the
     * given scope. That is, it will be automatically disposed alone with
     * all of the elements created as part of this BinBuilder when the
     * scope is closed.
     */
    public Pipeline buildPipeline(final GstScope scope) {
        return scope.manage(buildPipeline());
    }

    /**
     * Convert the current builder to a {@link Pipeline}
     */
    public Pipeline buildPipeline() {
        final Pipeline pipe = new Pipeline() {
            @Override
            public void dispose() {
                LOGGER.debug("disposing " + this + " with a ref count of " + this.getRefCount());
                super.dispose();
                disposeAll(current);
            }
        };
        return build(pipe, false);
    }

    /**
     * Convert the current builder to a {@link Bin}. This will also manage
     * the GhostPads. 
     */
    public Bin buildBin() {
        return buildBin(null, true);
    }

    /**
     * Convert the current builder to a {@link Bin} with the given name. This will also manage
     * the GhostPads. 
     */
    public Bin buildBin(final String binName) {
        return buildBin(binName, true);
    }

    /**
     * Convert the current builder to a {@link Bin} with the given name. If requested, this will 
     * also manage the GhostPads. 
     */
    public Bin buildBin(final String binName, final boolean ghostPads) {
        final Bin bin = binName == null ? new Bin() {
            @Override
            public void dispose() {
                LOGGER.debug("disposing " + this + " with a ref count of " + this.getRefCount());
                super.dispose();
                disposeAll(current);
            }
        } : new Bin(binName) {
            @Override
            public void dispose() {
                LOGGER.debug("disposing " + this + " with a ref count of " + this.getRefCount());
                super.dispose();
                disposeAll(current);
            }
        };
        return scope == null ? build(bin, ghostPads) : scope.manage(build(bin, ghostPads));
    }

    public <T extends Bin> T build(final T pipe, final boolean ghostPads) {
        addFullChainTo(pipe, current);
        Branch.linkFullChainOfBranches(current);
        if (ghostPads)
            createGhostPads(pipe);

        return pipe;
    }

    public List<Element> getEndElements() {
        final List<Element> ret = new ArrayList<>();
        appendEnds(current, ret);
        return ret;
    }

    private static void appendEnds(final Branch branch, final List<Element> ret) {
        if (branch.sinks.size() == 0) {
            final List<Element> cur = branch.stream().collect(Collectors.toList());
            if (cur.size() > 0)
                ret.add(cur.get(cur.size() - 1));
        } else {
            branch.sinks.stream().forEach(b -> appendEnds(b, ret));
        }
    }

    private static void disposeAll(final Branch cur) {
        final List<Branch> branches = cur.sinks;

        for (int b = branches.size() - 1; b >= 0; b--)
            disposeAll(branches.get(b));

        cur.disposeAll();
    }

    private static void addFullChainTo(final Bin pipe, final Branch current) {
        current.addAllTo(pipe);
        final List<Branch> next = current.sinks;
        if (next.size() > 0) {
            for (final Branch c : next)
                addFullChainTo(pipe, c);
        }
    }
}