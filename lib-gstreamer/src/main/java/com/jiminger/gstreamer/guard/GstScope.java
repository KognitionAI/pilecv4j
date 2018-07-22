package com.jiminger.gstreamer.guard;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Gst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.Branch;
import com.jiminger.gstreamer.BreakoutFilter;

import net.dempsy.util.Functional;

public class GstScope implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger(GstScope.class);

   private static int inited = 0;
   private static boolean testMode = true; // this is broken in GStreamer. Always in test mode.

   private final List<AutoCloseable> cleanups = new ArrayList<>();

   public synchronized static void testMode() {
      testMode = true;
   }

   public GstScope() {
      synchronized(GstScope.class) {
         if(inited == 0)
            Gst.init();
         inited++;
      }
      BreakoutFilter.init();
   }

   public GstScope(final Class<?> testClass) {
      this(testClass.getSimpleName());
   }

   public GstScope(final String appName) {
      this(appName, new String[] {});
   }

   public GstScope(final String appName, final String[] args) {
      synchronized(GstScope.class) {
         if(inited == 0)
            Gst.init(appName, args);
         inited++;
      }
   }

   public void register(final AutoCloseable ac) {
      synchronized(GstScope.class) {
         cleanups.add(ac);
      }
   }

   public <T extends Element> T manage(final T element) {
      register(new ElementWrap<T>(element));
      return element;
   }

   @Override
   public void close() {
      synchronized(GstScope.class) {
         while(cleanups.size() > 0) {
            Functional.<Exception>ignore(() -> cleanups.remove(cleanups.size() - 1).close(),
                  e -> LOGGER.error("Exception thrown durring closing of scope:", e));
         }
         cleanups.clear();

         if(!testMode) {
            inited--;
            if(inited == 0)
               Gst.deinit();
         } else {
            // reset the Branch counter.
            new Branch() {
               @Override
               public int hashCode() {
                  super.hackResetSequence();
                  return 0;
               }
            }.hashCode();
         }
      }
   }

}
