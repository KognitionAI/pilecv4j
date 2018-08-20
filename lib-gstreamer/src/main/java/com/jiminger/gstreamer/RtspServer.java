package com.jiminger.gstreamer;

import java.util.concurrent.atomic.AtomicLong;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.lowlevel.GMainContext;
import org.freedesktop.gstreamer.lowlevel.MainLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.guard.GstWrap;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class RtspServer implements AutoCloseable {
   private static final Logger LOGGER = LoggerFactory.getLogger("");
   private static final API API;
   private static final AtomicLong sequence = new AtomicLong(0);

   private final GMainContext mainCtx;
   private MainLoop mainLoop = null;
   private RtspServerObject server = null;
   private Thread mainLoopThread = null;

   static {
      API = Native.loadLibrary("gstrtspserver-1.0", API.class);
   }

   private static class RtspServerObject extends GstObject {
      public RtspServerObject(final Pointer ptr) {
         super(initializer(ptr));
      }
   }

   public RtspServer() {
      this(Gst.getMainContext());
   }

   public RtspServer(final GMainContext context) {
      this.mainCtx = context;
   }

   public void startServer() {
      startServer(-1);
   }

   public void startServer(final int port) {
      if(!isRunning()) {
         mainLoop = new MainLoop(mainCtx);
         server = new RtspServerObject(API.gst_rtsp_server_new());
         if(port > 0)
            API.gst_rtsp_server_set_address(server.getNativeAddress(), Integer.toString(port));

         API.gst_rtsp_server_attach(server.getNativeAddress(), mainCtx.getNativeAddress());

         mainLoopThread = new Thread(() -> {
            try {
               LOGGER.debug("Starting RtspServer MainLoop.");
               mainLoop.run();
            } catch(final RuntimeException rte) {
               LOGGER.error("Exception thrown in main loop.", rte);
               throw rte;
            }
         }, "RtspServer-MainLoop-" + sequence.getAndIncrement());
         mainLoopThread.start();
      } else
         throw new IllegalStateException("RtspServer is already running.");
   }

   public void play(final String uri, final String path) {
      if(!isRunning())
         startServer();

      try (final GstWrap<RtspServerObject> mounts = new GstWrap<>(new RtspServerObject(API.gst_rtsp_server_get_mount_points(server.getNativeAddress())));
            final GstWrap<RtspServerObject> factory = new GstWrap<>(new RtspServerObject(API.gst_rtsp_media_factory_new()));) {
         API.gst_rtsp_media_factory_set_launch(factory.obj.getNativeAddress(),
               "( uridecodebin uri=\"" + uri + "\" ! videoconvert ! x264enc ! rtph264pay pt=96 name=pay0 )");
         API.gst_rtsp_media_factory_set_shared(factory.obj.getNativeAddress(), true);

         API.gst_rtsp_mount_points_add_factory(mounts.obj.getNativeAddress(),
               path.startsWith("/") ? path : ("/" + path), factory.obj.getNativeAddress());
      }
   }

   public boolean isRunning() {
      return mainLoopThread != null && mainLoopThread.isAlive();
   }

   @Override
   public void close() {
      if(mainLoop.isRunning())
         mainLoop.quit();
      try {
         mainLoopThread.join(1000); // wait a full second
      } catch(final InterruptedException ie) {
         if(!mainLoopThread.isAlive())
            LOGGER.warn("Never shut down main loop for RtspServer.");
      }
   }

   public static void main(final String[] args) throws Exception {
      Gst.init();

      try (final RtspServer server = new RtspServer();) {
         server.play("file:///home/jim/kog/code/utilities/lib-gstreamer/src/test/resources/test-videos/Libertas-70sec.mp4", "/test");
         Thread.sleep(60000);
      }
   }

   private static interface API extends Library {
      // GstRTSPServer functions
      Pointer gst_rtsp_server_new();

      int gst_rtsp_server_attach(Pointer server, Pointer context);

      void gst_rtsp_server_set_address(Pointer server, String address);

      // GstRTSPMediaFactory functions
      Pointer gst_rtsp_media_factory_new();

      void gst_rtsp_media_factory_set_shared(Pointer factory, boolean val);

      boolean gst_rtsp_media_factory_is_shared(Pointer factory);

      void gst_rtsp_media_factory_set_launch(Pointer factory, String launch);

      // GstRTSPMountPoints functions
      Pointer gst_rtsp_server_get_mount_points(Pointer server);

      void gst_rtsp_mount_points_add_factory(final Pointer mounts, final String path, Pointer factory);
   }
}
