package com.jiminger.gstreamer.guard;

import java.util.ArrayList;
import java.util.List;

import org.freedesktop.gstreamer.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.util.GstUtils;

import net.dempsy.util.Functional;

public class GstScope implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger(GstScope.class);

   private final List<AutoCloseable> cleanups = new ArrayList<>();

   public GstScope() {
      this(GstUtils.DEFAULT_APP_NAME, new String[0]);
   }

   public GstScope(final Class<?> testClass) {
      this(testClass.getSimpleName());
   }

   public GstScope(final String appName) {
      this(appName, new String[] {});
   }

   public GstScope(final String appName, final String[] args) {
      GstUtils.safeGstInit(appName, args);
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

         GstUtils.safeGstDeinit();
      }
   }

}
