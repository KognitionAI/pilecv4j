package ai.kognition.pilecv4j.gstreamer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.ElementFactory;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.gstreamer.BinManager.OnError;
import ai.kognition.pilecv4j.gstreamer.util.GstUtils;

/**
 * This class can be used to build either a {@link Bin} or a {@link Pipeline} using a builder patter.
 * It encapsulates many of the boilerplate manipulations including naming, adding and linking elements.
 */
public class Branch {
    private static final Logger LOGGER = LoggerFactory.getLogger(Branch.class);
    final static AtomicInteger sequence = new AtomicInteger(0);

    final List<ElementHolder> elements = new ArrayList<>();
    private final Map<String, Object> binprops = new HashMap<>();
    private ElementHolder currentElement;

    public final String prefix;
    private String name = null;

    // =================================================================
    private final Pad afterDownstreamEndPad = null;
    private final Element afterDownstreamEndElement = null;
    private final String afterDownstreamEndElementPadName = null;
    // =================================================================

    // =================================================================
    private final Pad beforeUpstreamEndPad = null;
    private final Element beforeUpstreamEndElement = null;
    // private final String beforeSrcEndElementPadName = null;
    // =================================================================

    // =================================================================
    // This is the first and last (non-tee) elements of this
    // straight fork.
    ElementHolder first = null;
    ElementHolder last = null;
    // =================================================================

    // =================================================================
    // If this branch ends in a tee then the downstream branches will be
    // managed here.
    private Branch upstreamBranch = null;
    // The tee is owned by this Branch
    private Element tee = null;
    int padnum = 0;
    final List<Branch> sinks = new ArrayList<>();
    public Element multiqueue = null;
    // =================================================================

    // whether or not this branch will be the continuation of the parent
    // BinBuilder.
    boolean continueThisBranch = false;

    /**
     * Construct a branch where every element added without providing a name will have
     * the name prefixed by this string.
     */
    public Branch(final String prefix) {
        this.prefix = prefix;
    }

    /**
     * Construct a branch. Elements added without explicit names will be generated using
     * a suffix from monotonically increasing static sequence
     */
    public Branch() {
        this("");
    }

    /**
     * You can provide this branch a name. This is useful if you need to look it up later.
     */
    public Branch name(final String name) {
        this.name = name;
        return this;
    }

    /**
     * Get the previously provided name. This will return null if no name was provided using the
     * {@code name(String)} method.
     */
    public String getName() {
        return name;
    }

    /**
     * Add an element that has dynamic pads. This will manage linking the dynamic pads
     * as they're being added to the element.
     */
    public Branch delayed(final Element element) {
        add(new ElementHolder(element, true));
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
     * If you want to supply your own link logic for handling padAdded events then you can call this.
     * The most recently added element must be "delayed" or this will throw an IllegalStateException
     */
    public Branch padAddedCallback(final PadAddedCallback padAddedCallback) {
        if(currentElement == null)
            throw new IllegalStateException("You must add a delayed element before you can add a dynamic linker for the pads");
        else if(!currentElement.delayed)
            throw new IllegalStateException("You cannot dynamic link element " + currentElement.element
                + " because it's linking isn't \"delayed.\" That is, it's not registered as having \"Sometimes\" pads.");
        // else if(currentElement.padAddedCallback != null)
        // throw new IllegalStateException("Attempt to add a second padAddedCallback to element " + currentElement.element.getName());

        currentElement.padAddedCallback = padAddedCallback;
        return this;
    }

    /**
     * If you want to supply your own link logic then you can call this. The most recently
     * added element must be "delayed" or this will throw an IllegalStateException
     */
    public Branch dynamicLink(final DynamicLink linker) {
        if(currentElement == null)
            throw new IllegalStateException("You must add a delayed element before you can add a dynamic linker for the pads");

        currentElement.linker = linker;
        return this;
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
        add(new ElementHolder(e));
        return this;
    }

    /**
     * Identify this branch as the one where the BinManager can continue to add elements.
     */
    public Branch continueThisBranch() {
        continueThisBranch = true;
        return this;
    }

    /**
     * Iterate over all of the elements in this Branch. If this Branch ends in a
     * tee then the last element passed to the consumer will be the tee. If the
     * tee was set up with a multiqueue the multiqueue will NOT be considered
     * an element in this tee.
     */
    public Branch forEach(final Consumer<Element> c) {
        elements.stream().map(e -> e.element).forEach(c);
        if(tee != null)
            c.accept(tee);
        return this;
    }

    /**
     * Set a property on the most recently added element or if no element
     * has been added yet, on the Bin itself.
     */
    public Branch with(final String name, final Object value) {
        if(currentElement == null)
            binprops.put(name, value);
        else
            currentElement.element.set(name, value);
        return this;
    }

    /**
     * Get the sink (upstream most) element which may be the tee if no other
     * elements are in this Branch.
     */
    public Element getSinkElement() {
        final Element first = stream().findFirst().orElse(tee);
        if(first == null)
            throw new IllegalStateException(
                "There doesn't appear to be any elements on this " + Branch.class.getSimpleName() + ". Can't retrieve sink element.");
        return first;
    }

    /**
     * Get the last element of this branch. If this branch terminates in a tee then
     * that's the element that will be returned. Otherwise it will return the last element
     * that was added in the chain. If there aren't any then it will return null.
     */
    public Element getLastElement() {
        return (tee != null) ? tee : (elements.size() > 0 ? elements.get(elements.size() - 1).element : null);
    }

    /**
     * Return a stream of all of the elements include a terminating tee if it exists.
     */
    public Stream<Element> stream() {
        return tee == null ? elements.stream().map(l -> l.element) : Stream.concat(elements.stream().map(l -> l.element), Stream.of(tee));
    }

    /**
     * Set an error handler for an error that happens on the most recently added Element.
     *
     * @throws IllegalStateException if there is no element on the Branch yet, or there is already an
     *     error handler on the current element.
     */
    public Branch onError(final OnError cb) throws IllegalStateException {
        if(currentElement == null)
            throw new IllegalStateException("You must add an element before attaching an error handler to it.");
        if(currentElement.errorHandler != null)
            throw new IllegalStateException(
                "You should only set a single " + OnError.class.getSimpleName() + " handler for any given element. The element \"" + currentElement.element
                    + "\" appears to have more than one set.");
        this.currentElement.errorHandler = cb;
        return this;
    }

    /**
     * Add all of the elements, including any terminating tee, to the bin.
     */
    public Branch addAllTo(final Bin bin) {
        for(final ElementHolder e: elements)
            GstUtils.safeAdd(bin, e.element);

        if(tee != null)
            GstUtils.safeAdd(bin, tee);

        if(multiqueue != null)
            GstUtils.safeAdd(bin, multiqueue);

        return this;
    }

    /**
     * Link all of the elements, including any terminating tee. The elements should
     * have already been added to a common Bin.
     */
    public Branch linkAll() {
        // each break represents a delayed
        ElementHolder prev = null;
        for(final ElementHolder e: elements) {

            if(prev != null) {
                if(prev.delayed) {
                    prev.element.connect(getPadAddedFromDynamicLink(e.element, prev.padAddedCallback));
                    if(LOGGER.isDebugEnabled())
                        prev.element.connect((Element.PAD_REMOVED)(element, pad) -> {
                            LOGGER.debug("REMOVING PAD: {} pad: {} ", element, pad);
                        });
                    // // TODO: I don't think this is right
                    // last = null;
                } else {
                    if(prev.linker != null)
                        prev.linker.link(prev.element, e.element);
                    else {
                        if(!prev.element.link(e.element))
                            LOGGER.warn("Link between " + prev.element + " and " + e.element + " failed.");
                    }
                }
            }

            prev = e;
        }

        if(tee != null) {
            if(prev != null) { // tee might be first.
                if(prev.delayed) {
                    prev.element.connect(getPadAddedFromDynamicLink(tee, defaultDelayedCallback));
                    if(LOGGER.isDebugEnabled())
                        prev.element.connect((Element.PAD_REMOVED)(element, pad) -> {
                            LOGGER.debug("REMOVING PAD: {} pad: {} ", element, pad);
                        });
                } else {
                    if(prev.linker != null)
                        prev.linker.link(prev.element, tee);
                    else
                        prev.element.link(tee);
                }
            }
        }

        return this;
    }

    /**
     * Given a Branch that's terminated in a tee, add another fork off the terminating tee. This
     * can be done on a playing Bin but you're responsible for making sure the Branch elements
     * have already been added to the Bin.
     */
    public Branch linkNewBranch(final Branch branch) {
        return linkNewBranch(branch.first.element);
    }

    /**
     * Given a Branch that's terminated in a tee, add another fork off the terminating tee. This
     * can be done on a playing Bin but you're responsible for making sure the Branch elements
     * have already been added to the Bin.
     */
    public Branch linkNewBranch(final Element branch) {
        if(tee == null)
            throw new IllegalStateException("You cannot dynamically attach a new branch unless the current branch ends in a tee");

        final List<Pad> branchSinkPads = branch.getSinkPads();
        if(branchSinkPads.size() != 1)
            throw new RuntimeException(
                "First element in branch (" + branch + ") has the wrong number of sink pad (" + branchSinkPads.size() + "). It requires exactly one.");
        final Pad branchSinkPad = branchSinkPads.get(0);

        final int lpadnum = padnum;
        padnum++; // if there's any failures we want this consistent.

        final Pad teeSrcPad = tee.getRequestPad("src_" + lpadnum);
        if(multiqueue != null) {
            final Pad mqSinkPad = multiqueue.getRequestPad("sink_" + lpadnum);
            GstUtils.safeLink(teeSrcPad, mqSinkPad);

            final String mqSrcPadName = "src_" + lpadnum;
            final Pad mqSrcMqPad = multiqueue.getSrcPads().stream().filter(p -> mqSrcPadName.equals(p.getName())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Multiqueue" + multiqueue + " seems to be missing the source pad " + mqSrcPadName));
            GstUtils.safeLink(mqSrcMqPad, branchSinkPad);
        } else
            GstUtils.safeLink(teeSrcPad, branchSinkPad);

        return this;
    }

    public Pad forkYou() {
        if(tee == null)
            throw new IllegalStateException("Cannot add a fork from a Branch that isn't terminated in a tee.");

        final int lpadnum = padnum;
        padnum++; // if there's any failures we want this consistent.

        final Pad teeSrcPad = tee.getRequestPad("src_" + lpadnum);
        if(multiqueue != null) {
            final Pad mqSinkPad = multiqueue.getRequestPad("sink_" + lpadnum);
            GstUtils.safeLink(teeSrcPad, mqSinkPad);

            final String mqSrcPadName = "src_" + lpadnum;
            final Pad mqSrcMqPad = multiqueue.getSrcPads().stream().filter(p -> mqSrcPadName.equals(p.getName())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Multiqueue" + multiqueue + " seems to be missing the source pad " + mqSrcPadName));
            return mqSrcMqPad;
        } else
            return teeSrcPad;
    }

    Stream<Branch> allBranches() {
        return Stream.concat(Stream.of(this), sinks.stream().flatMap(b -> b.allBranches()));
    }

    void connectDownstreams(final String teeName, final boolean autoMultiQueue, final Branch... downstream) {
        if(alreadyTeminatedDownstreamEnd())
            throw new IllegalStateException("You cannot make this branch " + this
                + " upstream of a tee because it's already terminated at the sink end with either another tee or a downstream element/pad.");

        Arrays.stream(downstream).forEach(b -> {
            if(b.alreadyTeminatedSrcEnd())
                throw new IllegalStateException(
                    "You cannot make this branch " + b
                        + " downstream of a tee because it's already terminated at the src end with either another element/pad.");

            b.upstreamBranch = this;
            this.sinks.add(b);
        });
        final String nm = teeName == null ? nextName("tee") : teeName;
        tee = ElementFactory.make("tee", nm);

        if(autoMultiQueue)
            multiqueue = new ElementBuilder("multiqueue").build();
    }

    void closeAll() {
        if(tee != null)
            tee.close();
        if(multiqueue != null)
            multiqueue.close();

        for(int i = elements.size() - 1; i >= 0; i--) {
            final Element ce = elements.get(i).element;
            if(LOGGER.isTraceEnabled())
                LOGGER.trace("closing {} ", ce.getName());
            ce.close();
        }
    }

    static void linkFullChainOfBranches(final Branch current) {
        current.linkAll();

        // current.sinks will only be set if tee is also set.
        final List<Branch> next = current.sinks;

        final Element tee = current.tee;
        final Element multiqueue = current.multiqueue;

        if(next.size() > 0) {
            for(final Branch c: next) {
                final int lpadnum = current.padnum;
                current.padnum++; // if there's any failures we want this consistent.

                final List<Pad> branchSinkPads = c.first.element.getSinkPads();
                if(branchSinkPads.size() != 1)
                    throw new RuntimeException(
                        "First element in branch (" + c + ") has the wrong number of sink pad (" + branchSinkPads.size() + "). It requires exactly one.");
                final Pad branchSinkPad = branchSinkPads.get(0);
                final Pad teeSrcPad = tee.getRequestPad("src_" + lpadnum);
                // final Pad teeSrcPad = new Pad("src_" + padnum++, PadDirection.SRC);
                // tee.addPad(teeSrcPad);
                if(multiqueue != null) {
                    final Pad sinkMqPad = multiqueue.getRequestPad("sink_" + lpadnum);
                    GstUtils.safeLink(teeSrcPad, sinkMqPad);
                    final String srcPadName = "src_" + lpadnum;
                    final Pad srcMqPad = multiqueue.getSrcPads().stream().filter(p -> srcPadName.equals(p.getName())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Multiqueue" + multiqueue + " seems to be missing the source pad " + srcPadName));
                    GstUtils.safeLink(srcMqPad, branchSinkPad);
                } else
                    GstUtils.safeLink(teeSrcPad, branchSinkPad);
                linkFullChainOfBranches(c);
            }
        }

        // we also need to link downstream pads/elements. ... this should only be set if sinks/tee is not
        Pad downstreamPad = null;
        if(current.afterDownstreamEndPad != null)
            downstreamPad = current.afterDownstreamEndPad;
        else if(current.afterDownstreamEndElement != null) {
            final Element element = current.afterDownstreamEndElement;

            if(current.afterDownstreamEndElementPadName != null) {
                downstreamPad = element.getSinkPads().stream().filter(p -> current.afterDownstreamEndElementPadName.equals(p.getName())).findFirst()
                    .orElse(null);
                if(downstreamPad == null)
                    throw new IllegalStateException(
                        "Couldn't find a pad named " + current.afterDownstreamEndElementPadName + " among " + element.getSinkPads());

            } else {
                final List<Pad> sinkPads = element.getSinkPads();
                if(sinkPads == null || sinkPads.size() != 1)
                    throw new IllegalArgumentException(element.getName() + " cannot be set upstream to the branch " + current
                        + " because it has the wrong number of source pads (" + (sinkPads == null ? 0 : sinkPads.size()) + ").");
                downstreamPad = sinkPads.get(0);
            }
        }

        if(downstreamPad != null) {
            // should be a sink pad
            // get the Branch's src pad
            // current.linkAll set current.last to the last element.
            final List<Pad> srcPads = current.last.element.getSrcPads();
            if(srcPads == null || srcPads.size() != 1)
                // can't link the downstream pad
                throw new IllegalStateException("Cannot link to downstream pad " + downstreamPad
                    + " because there are the wrong number of src pads emerging from the branch " + current + ".");
            srcPads.get(0).link(downstreamPad);
        }
    }

    protected void hackResetSequence() {
        sequence.set(0);
    }

    private Branch add(final ElementHolder e) {
        if(alreadyTeminatedDownstreamEnd())
            throw new IllegalStateException("You cannot add an element to a Branch once the branch has been terminated with a pad or element.");

        elements.add(e);
        if(first == null)
            first = e;
        last = e;
        currentElement = e;
        return this;
    }

    private boolean alreadyTeminatedDownstreamEnd() {
        return(afterDownstreamEndPad != null || afterDownstreamEndElement != null || tee != null);
    }

    private boolean alreadyTeminatedSrcEnd() {
        return(beforeUpstreamEndPad != null || beforeUpstreamEndElement != null || upstreamBranch != null);
    }

    private String nextName(final String basename) {
        return prefix + basename + sequence.getAndIncrement();
    }

    private Element.PAD_ADDED getPadAddedFromDynamicLink(final Element next, final PadAddedCallback linker) {
        return new PadAddedWrapper(next, linker);
    }

    private static PadAddedCallback defaultDelayedCallback = (element, pad, sink) -> {
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace("Default delayed linking element " + element + " pad " + pad + " to " + sink);
        }
        if(pad.isLinked())
            return;
        final List<Pad> nextPads = sink.getSinkPads();
        if(nextPads != null && nextPads.size() > 0)
            GstUtils.safeLink(pad, nextPads.get(0));
        else
            LOGGER.warn("Cannot link " + element + " to " + sink + " dynamically because there are no sink pads.");
    };

    private static final class PadAddedWrapper implements Element.PAD_ADDED {
        private final Element next;
        private final PadAddedCallback linker;

        private PadAddedWrapper(final Element next, final PadAddedCallback linker) {
            this.next = next;
            this.linker = linker;
        }

        @Override
        public void padAdded(final Element element, final Pad pad) {
            LOGGER.trace("Delayed linking for element " + element + " pad " + pad);
            // TODO Auto-generated method stub
            linker.padAdded(element, pad, next);

            // my job is done here.
            // element.disconnect(this);
        }
    }

    static class ElementHolder {
        public final Element element;
        public final boolean delayed;
        public PadAddedCallback padAddedCallback;
        public DynamicLink linker;
        public OnError errorHandler;

        public ElementHolder(final Element element, final boolean delayed) {
            this.element = element;
            this.delayed = delayed;
            this.padAddedCallback = defaultDelayedCallback;
            this.linker = null;
            this.errorHandler = null;
        }

        public ElementHolder(final Element element) {
            this(element, false);
        }
    }

}
