package com.jiminger.gstreamer;

import java.util.List;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadDirection;
import org.freedesktop.gstreamer.Pipeline;

/**
 *  This class can be used to build either a {@link Bin} or a {@link Pipeline} using a builder patter.
 *  It encapsulates many of the boilerplate manipulations including naming, adding and linking elements.
 */
public class BinBuilder {
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
     * Add an element that has static pads. 
     */
    public BinBuilder add(final Element e) {
        current.add(e);
        return this;
    }

    /**
     * Set a property on the most recently added element.
     */
    public BinBuilder with(final String name, final String value) {
        current.with(name, value);
        return this;
    }

    public BinBuilder tee(final Branch... branches) {
        current.connectDownstreams(branches);
        return this;
    }

    /**
     * Convert the current builder to a {@link Pipeline}
     */
    public Pipeline buildPipeline() {
        final Pipeline pipe = new Pipeline();
        current.addAllTo(pipe);
        build(pipe, current, 0);
        return pipe;
    }

    private static int build(final Bin pipe, final Branch current, int teeNum) {
        current.linkAll(pipe);
        final List<Branch> next = current.sinks;
        final String binName = pipe.getName();
        if (next.size() > 0) {
            // add a Tee
            final Element tee = ElementFactory.make("tee", binName + teeNum);
            pipe.add(tee);
            current.last.link(tee);
            int padnum = 0;
            for (final Branch c : next) {
                final List<Pad> branchSinkPads = c.first.getSinkPads();
                if (branchSinkPads.size() != 1)
                    throw new RuntimeException("First element in branch (" + c + ") has the wrong number of sink pad (" + branchSinkPads.size()
                            + "). It requires exactly one.");
                c.addAllTo(pipe);
                final Pad branchSinkPad = branchSinkPads.get(0);
                final Pad teeSrcPad = new Pad("src_" + padnum++, PadDirection.SRC);
                tee.addPad(teeSrcPad);
                teeSrcPad.link(branchSinkPad);
                teeNum = build(pipe, c, teeNum + 1);
            }
        }
        return teeNum;
    }

    /**
     * Convert the current builder to a {@link Bin}. This will also manage
     * the GhostPads. Currently it assumes there's a single src pad and
     * a single sink pad at the ends of the chain of elements that have been
     * added.
     */
    public Bin buildBin() {
        if (current.sinks.size() > 0)
            throw new RuntimeException("Can't build a Bin from a graph with a Tee in it.");
        return current.buildBin();
    }

}