package com.jiminger.gstreamer.util;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkReturn;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.lowlevel.GstNative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class GstUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GstUtils.class);

    static {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        java.util.logging.Logger.getLogger("").setLevel(Level.WARNING);
    }

    public static int GST_DEBUG_GRAPH_SHOW_MEDIA_TYPE = (1 << 0);
    public static int GST_DEBUG_GRAPH_SHOW_CAPS_DETAILS = (1 << 1);
    public static int GST_DEBUG_GRAPH_SHOW_NON_DEFAULT_PARAMS = (1 << 2);
    public static int GST_DEBUG_GRAPH_SHOW_STATES = (1 << 3);
    public static int GST_DEBUG_GRAPH_SHOW_ALL = ((1 << 4) - 1);

    private static interface GstDebugAPI extends com.sun.jna.Library {
        void gst_debug_bin_to_dot_file(Bin bin, int details, String fileName);
    }

    private static final GstDebugAPI gst = GstNative.load(GstDebugAPI.class);

    public static final void gstDebugBinToDotFile(final Bin bin, final int details,
            final String fileName) {
        gst.gst_debug_bin_to_dot_file(bin, details, fileName);
    }

    public static void setGstLogLevel(final Level level) {
        setGstLogLevel("", level);
    }

    public static void setGstLogLevel(final String logger, final Level level) {
        java.util.logging.Logger.getLogger(logger).setLevel(level);
    }

    public static final Bus.EOS endOnEOS(final Bin bin) {
        final Bus.EOS ret = (object) -> {
            // if (LOGGER.isDebugEnabled())
            LOGGER.info("end-of-stream: {}", object);
            bin.stop();
            Gst.quit();
        };
        return ret;
    }

    public static final Bus.ERROR endOnError(final Bin bin, final Bus.ERROR errCb) {
        final Bus.ERROR ret = (object, code, msg) -> {
            LOGGER.error("error:" + object + " code:" + code + " " + msg);
            // bin.stop();
            // Gst.quit();
            if (errCb != null)
                errCb.errorMessage(object, code, msg);
        };
        return ret;
    }

    // public static final Bus.STATE_CHANGED printStateChange = (final GstObject source, final State old, final State current,
    // final State pending) -> {
    // LOGGER.debug("State of " + source + " changed from " + old + " to " + current + " with " + pending + " pending.");
    // };

    public static void instrument(final Pipeline pipe, final Bus.ERROR errCb) {
        final Bus bus = pipe.getBus();
        bus.connect(endOnEOS(pipe));
        bus.connect(endOnError(pipe, errCb));
        if (LOGGER.isDebugEnabled())
            // bus.connect(printStateChange);
            bus.connect((Bus.STATE_CHANGED) (s, o, c, p) -> {
                if (s == pipe || s.getParent() == pipe)
                    LOGGER.debug("State of " + s + " changed from " + o + " to " + c + " with " + p + " pending.");
            });
    }

    public static void instrument(final Pipeline pipe) {
        instrument(pipe, null);
    }

    private static final String indent = "    ";

    public static void printDetails(final Pad p, final PrintStream out, final String prefix) {
        out.println(prefix + indent + p);
        out.println(prefix + indent + "caps:      " + p.getCaps());
        out.println(prefix + indent + "allowed:   " + p.getAllowedCaps());
        out.println(prefix + indent + "negotiated:" + p.getNegotiatedCaps());
        final Pad peer = p.getPeer();
        if (peer != null) {
            final Element peerElement = peer.getParentElement();
            out.println(prefix + indent + "peer:" + (peerElement == null ? "[null element]" : peerElement.getName()) + ":" + peer);
        } else
            out.println(prefix + indent + "not connected");
        out.println();
    }

    public static void printDetails(final Bin pipe) {
        printDetails(pipe, System.out);
    }

    public static void printDetails(final Bin pipe, final PrintStream out) {
        printDetails(pipe, out, 0);
    }

    public static void printDetails(final Element e) {
        printDetails(e, System.out);
    }

    public static void printDetails(final Element e, final PrintStream out) {
        printDetails(e, out, 0);
    }

    public static Pad getSrcPad(final Element e) {
        final List<Pad> pads = e.getSrcPads();
        if (pads == null || pads.size() == 0)
            throw new IllegalStateException("Attempted to get the single source pad from " + e + " but there aren't any.");
        if (pads.size() > 1)
            throw new IllegalStateException("Attempted to get the single source pad from " + e + " but there several [" + pads + "].");
        return pads.get(0);
    }

    public static void link(final Element src, final Pad sink) {
        final PadLinkReturn plret = GstUtils.getSrcPad(src).link(sink);
        if (plret != PadLinkReturn.OK)
            throw new RuntimeException("Failed to link the src element " + src + " with the sink pad " + sink + ". Result was " + plret);
    }

    public static PadLinkReturn safeLink(final Pad srcPad, final Pad sinkPad) {
        final PadLinkReturn linkResult = srcPad.link(sinkPad);
        if (PadLinkReturn.OK != linkResult)
            throw new IllegalStateException("Link failed (" + linkResult + ") linking src pad " + srcPad + " from element " + srcPad.getParent()
                    + " to sink pad " + sinkPad + " from element " + sinkPad.getParent());
        return linkResult;
    }

    public static void safeAdd(final Bin bin, final Element e) {
        if (bin == null)
            throw new NullPointerException("Can't add element " + e + " to a null bin.");
        if (e == null)
            throw new NullPointerException("Can't add null element to bin " + bin);
        if (!bin.add(e))
            throw new IllegalStateException("Adding " + e + " to Bin " + bin + " failed ");
    }

    private static void printDetails(final Bin pipe, final PrintStream out, final int depth) {
        final List<Element> elements = pipe.getElements();
        final int numEl = elements.size();
        for (int i = numEl - 1; i >= 0; i--)
            printDetails(elements.get(i), out, depth);
    }

    private static void printDetails(final Element e, final PrintStream out, final int depth) {
        String tmp = "";
        for (int i = 0; i < depth; i++)
            tmp += indent + indent;
        final String prefix = tmp;

        // TODO: Put back the state
        out.println(prefix + "Element:" + e + " (child of: " + e.getParent() + ") state:");
        out.println(prefix + "                             " + e.getState(500, TimeUnit.MILLISECONDS));
        out.println(prefix + "  -------------------");
        out.println(prefix + "  sink:");
        e.getSinkPads().forEach(p -> printDetails(p, out, prefix));
        out.println(prefix + "  -------------------");
        out.println(prefix + "  source:");
        e.getSrcPads().forEach(p -> printDetails(p, out, prefix));
        out.println(prefix + "  -------------------");

        if (e instanceof Bin) {
            out.println(prefix + indent + indent + "=======================================================");
            printDetails((Bin) e, out, depth + 1);
            out.println(prefix + indent + indent + "=======================================================");
        }

    }

}
