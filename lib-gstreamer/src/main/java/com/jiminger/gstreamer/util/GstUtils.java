package com.jiminger.gstreamer.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.commons.io.output.WriterOutputStream;
import org.freedesktop.gstreamer.Bin;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.GhostPad;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadLinkException;
import org.freedesktop.gstreamer.PadLinkReturn;
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

   public static final void gstDebugBinToDotFile(final Bin bin, final int details,            // bus.connect(printStateChange);

         final String fileName) {
      gst.gst_debug_bin_to_dot_file(bin, details, fileName);
   }

   public static void setGstLogLevel(final Level level) {
      setGstLogLevel("", level);
   }

   public static void setGstLogLevel(final String logger, final Level level) {
      java.util.logging.Logger.getLogger(logger).setLevel(level);
   }

   public static void stopBinOnEOS(final Bin pipe, final CountDownLatch latch) {
      final Bus bus = pipe.getBus();
      bus.connect(getEndOnEOSCallback(pipe, latch));
   }

   public static <T extends Bin> void onError(final T pipe, final Bus.ERROR errCb) {
      final Bus bus = pipe.getBus();
      bus.connect(errCb);
   }

   private static boolean gstInited = false; // double checked locking ... OH NO!!!!

   /**
    * Safely call Gst.init(). This manages multiple calls and makes sure it's only called
    * once.
    */
   public static void safeGstInit() {
      if(!gstInited) {
         synchronized(GstUtils.class) {
            if(!gstInited) {
               Gst.init();
               gstInited = true;
            }
         }
      }
   }

   private static final Bus.EOS getEndOnEOSCallback(final Bin bin, final CountDownLatch latch) {
      final Bus.EOS ret = (object) -> {
         LOGGER.info("end-of-stream: {}", object);
         bin.stop();
         if(latch != null)
            latch.countDown();
      };
      return ret;
   }

   private static final String indent = "    ";

   public static void printDetails(final Pad p, final PrintStream out, final String prefix) {
      out.println(prefix + indent + p);
      out.println(prefix + indent + "caps:      " + p.getCaps());
      out.println(prefix + indent + "allowed:   " + p.getAllowedCaps());
      out.println(prefix + indent + "negotiated:" + p.getNegotiatedCaps());
      final Pad peer = p.getPeer();
      if(peer != null) {
         final Element peerElement = peer.getParentElement();
         out.println(prefix + indent + "peer:" + (peerElement == null ? "[null element]" : peerElement.getName()) + ":" + peer);
      } else
         out.println(prefix + indent + "not connected");

      if(p instanceof GhostPad) {
         out.print(prefix + indent + "Ghost pad proxy's: ");
         final Pad proxy = ((GhostPad)p).getTarget();
         if(proxy == null)
            out.println(prefix + indent + indent + "nothing");
         else
            out.println(proxy + " on " + proxy.getParent());
      }

      out.println();
   }

   public static Pad getSrcPad(final Element e) {
      final List<Pad> pads = e.getSrcPads();
      if(pads == null || pads.size() == 0)
         throw new IllegalStateException("Attempted to get the single source pad from " + e + " but there aren't any.");
      if(pads.size() > 1)
         throw new IllegalStateException("Attempted to get the single source pad from " + e + " but there several [" + pads + "].");
      return pads.get(0);
   }

   public static void safeLink(final Element src, final Pad sink) {
      safeLink(GstUtils.getSrcPad(src), sink);
   }

   public static void safeLink(final Pad srcPad, final Pad sinkPad) {
      try {
         srcPad.link(sinkPad);
      } catch(final PadLinkException ple) {
         if(ple.getLinkResult() != PadLinkReturn.WAS_LINKED) {

            LOGGER.error("Failed to link source " + padDescription(srcPad)
                  + "To sink " + padDescription(sinkPad));

            throw ple;
         }
      }
   }

   public static void safeAdd(final Bin bin, final Element e) {
      if(bin == null)
         throw new NullPointerException("Can't add element " + e + " to a null bin.");
      if(e == null)
         throw new NullPointerException("Can't add null element to bin " + bin);
      if(e.getParent() == bin)
         return;
      if(!bin.add(e))
         throw new IllegalStateException("Adding " + e + " to Bin " + bin + " failed ");
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

   private static String padDescription(final Pad pad) {
      final GstObject parent = pad.getParent();

      final StringWriter strHolder;
      try (final StringWriter srcPadDescPw = new StringWriter();
            PrintStream srcPadWriter = new PrintStream(new WriterOutputStream(srcPadDescPw, Charset.defaultCharset()))) {
         strHolder = srcPadDescPw;
         GstUtils.printDetails(pad, srcPadWriter, "");
      } catch(final IOException ioe) {
         // this shouldn't be possible
         LOGGER.error("An impossible exception happened.");
         throw new RuntimeException(ioe);
      }

      return "Element:" + parent + " pad:" + strHolder.toString();
   }

   private static void printDetails(final Bin pipe, final PrintStream out, final int depth) {
      final List<Element> elements = pipe.getElements();
      final int numEl = elements.size();
      for(int i = numEl - 1; i >= 0; i--)
         printDetails(elements.get(i), out, depth);
   }

   private static void printDetails(final Element e, final PrintStream out, final int depth) {
      String tmp = "";
      for(int i = 0; i < depth; i++)
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

      if(e instanceof Bin) {
         out.println(prefix + indent + indent + "=======================================================");
         printDetails((Bin)e, out, depth + 1);
         out.println(prefix + indent + indent + "=======================================================");
      }
   }

}
