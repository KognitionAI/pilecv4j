package com.jiminger.gstreamer.util;

import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;

import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.jiminger.gstreamer.guard.ElementWrap;

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

    public static final Bus.EOS endOnEOS(final Bin bin) {
        final Bus.EOS ret = (object) -> {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("end-of-stream: " + object);
            bin.stop();
            Gst.quit();
        };
        return ret;
    }

    public static final Bus.ERROR endOnError(final Bin bin, final Bus.ERROR errCb) {
        final Bus.ERROR ret = (object, code, msg) -> {
            LOGGER.error("error:" + object + " code:" + code + " " + msg);
            bin.stop();
            Gst.quit();
            if (errCb != null)
                errCb.errorMessage(object, code, msg);
        };
        return ret;
    }

    public static final Bus.STATE_CHANGED printStateChange = (final GstObject source, final State old, final State current,
            final State pending) -> {
        LOGGER.debug("State of " + source + " changed from " + old + " to " + current + " with " + pending + " pending.");
    };

    public static void instrument(final Pipeline pipe, final Bus.ERROR errCb) {
        final Bus bus = pipe.getBus();
        bus.connect(endOnEOS(pipe));
        bus.connect(endOnError(pipe, errCb));
        if (LOGGER.isDebugEnabled())
            bus.connect(printStateChange);
    }

    public static void instrument(final Pipeline pipe) {
        instrument(pipe, null);
    }

    public static void instrument(final ElementWrap<Pipeline> pipe, final Bus.ERROR errCb) {
        instrument(pipe.element, errCb);
    }

    public static void instrument(final ElementWrap<Pipeline> pipe) {
        instrument(pipe, null);
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

    public static void printDetails(final Bin pipe, final PrintStream out) {
        printDetails(pipe, out, 0);
    }

    public static void printDetails(final Element e) {
        printDetails(e, System.out);
    }

    public static void printDetails(final Element e, final PrintStream out) {
        printDetails(e, out, 0);
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
}
