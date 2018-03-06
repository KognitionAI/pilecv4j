package com.jiminger.gstreamer;

import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.MiniObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
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

    public static void setGstLogLevel(final Level level) {
        setGstLogLevel("", level);
    }

    public static void setGstLogLevel(final String logger, final Level level) {
        java.util.logging.Logger.getLogger(logger).setLevel(level);
    }

    public static final Bus.EOS endOnEOS = (object) -> {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("end-of-stream: " + object);
        Gst.quit();
    };

    public static final Bus.ERROR endOnError = (object, code, msg) -> {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("error:" + object + " code:" + code + " " + msg);
        Gst.quit();
    };

    public static final Bus.STATE_CHANGED printStateChange = (final GstObject source, final State old, final State current,
            final State pending) -> {
        LOGGER.debug("State of " + source + " changed from " + old + " to " + current + " with " + pending + " pending.");
    };

    public static void instrument(final Pipeline pipe) {
        final Bus bus = pipe.getBus();
        bus.connect(endOnEOS);
        bus.connect(endOnError);
        if (LOGGER.isDebugEnabled())
            bus.connect(printStateChange);
    }

    public static void dispose(final MiniObject obj) {
        if (obj != null)
            obj.dispose();
    }

    private static final String indent = "    ";

    public static void printDetails(final Pad p, final PrintStream out, final String prefix) {
        out.println(prefix + indent + p);
        out.println(prefix + indent + "caps:      " + p.getCaps());
        out.println(prefix + indent + "allowed:   " + p.getAllowedCaps());
        out.println(prefix + indent + "negotiated:" + p.getNegotiatedCaps());
    }

    public static void printDetails(final Bin pipe) {
        printDetails(pipe, System.out);
    }

    public static void printDetails(final Element e) {
        printDetails(e, System.out, 0);
    }

    public static void printDetails(final Element e, final PrintStream out) {
        printDetails(e, out, 0);
    }

    public static void printDetails(final Element e, final PrintStream out, final int depth) {
        String tmp = "";
        for (int i = 0; i < depth; i++)
            tmp += indent + indent;
        final String prefix = tmp;

        out.println(prefix + "Element:" + e);
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

    public static void printDetails(final Bin pipe, final PrintStream out) {
        printDetails(pipe, out, 0);
    }

    public static void printDetails(final Bin pipe, final PrintStream out, final int depth) {
        final List<Element> elements = pipe.getElements();
        final int numEl = elements.size();
        for (int i = numEl - 1; i >= 0; i--)
            printDetails(elements.get(i), out, depth);
    }
}
