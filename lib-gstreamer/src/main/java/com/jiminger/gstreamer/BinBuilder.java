package com.jiminger.gstreamer;

import java.util.List;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadDirection;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.guard.ElementWrap;

/**
 *  This class can be used to build either a {@link Bin} or a {@link Pipeline} using a builder pattern.
 *  It encapsulates many of the boilerplate manipulations including naming, adding and linking elements.
 */
public class BinBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinBuilder.class);

    final Branch current = new Branch();

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
        linkFullChain(current);
        return this;
    }

    /**
     * Convert the current builder to a {@link Pipeline}
     */
    public ElementWrap<Pipeline> buildPipeline() {
        final Pipeline pipe = new Pipeline();
        return build(pipe);
    }

    /**
     * Convert the current builder to a {@link Bin}. This will also manage
     * the GhostPads. 
     */
    public ElementWrap<Bin> buildBin() {
        return buildBin(null, true);
    }

    /**
     * Convert the current builder to a {@link Bin} with the given name. This will also manage
     * the GhostPads. 
     */
    public ElementWrap<Bin> buildBin(final String binName) {
        return buildBin(binName, true);
    }

    /**
     * Convert the current builder to a {@link Bin} with the given name. If requested, this will also manage
     * the GhostPads. 
     */
    public ElementWrap<Bin> buildBin(final String binName, final boolean ghostPads) {
        final Bin bin = binName == null ? new Bin() : new Bin(binName);
        return build(bin);
    }

    public <T extends Bin> ElementWrap<T> build(final T pipe) {
        addFullChainTo(pipe, current);
        linkFullChain(current);

        return new ElementWrap<T>(pipe) {

            @Override
            public void close() {
                LOGGER.debug("disposing " + element + " with a ref count of " + element.getRefCount());
                element.dispose();
                disposeAll(current);
            }
        };
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

    private static void linkFullChain(final Branch current) {
        current.linkAll();
        final List<Branch> next = current.sinks;
        final Element tee = current.tee;
        if (next.size() > 0) {
            int padnum = 0;
            for (final Branch c : next) {
                final List<Pad> branchSinkPads = c.first.getSinkPads();
                if (branchSinkPads.size() != 1)
                    throw new RuntimeException("First element in branch (" + c + ") has the wrong number of sink pad (" + branchSinkPads.size()
                            + "). It requires exactly one.");
                final Pad branchSinkPad = branchSinkPads.get(0);
                final Pad teeSrcPad = new Pad("src_" + padnum++, PadDirection.SRC);
                tee.addPad(teeSrcPad);
                teeSrcPad.link(branchSinkPad);
                linkFullChain(c);
            }
        }
    }
}