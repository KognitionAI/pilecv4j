package com.jiminger.gstreamer;

import static net.dempsy.util.Functional.ignore;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.GstObject;
import org.freedesktop.gstreamer.lowlevel.GMainContext;
import org.freedesktop.gstreamer.lowlevel.GlibAPI;
import org.freedesktop.gstreamer.lowlevel.MainLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jiminger.gstreamer.guard.GstScope;
import com.jiminger.gstreamer.guard.GstWrap;
import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class RtspServer implements AutoCloseable {

   // matches the enum GstRTSPFilterResult defined in rtsp-session.h
   public static final int GST_RTSP_FILTER_REMOVE = 0;

   /**
    * using DEFAULT_PORT in the startServer command tells the underlying gstreamer
    * rtsp server to use its default port. That's currently 8554.
    */
   public static final int USE_DEFAULT_PORT = -1;

   /**
    * Passing EPHEMERAL_PORT to startServer will allow the underlying rtsp server
    * to bind to an ephemeral port. You can find out what port was chosen by uing
    * getPort.
    */
   public static final int USE_EPHEMERAL_PORT = 0;

   private static final Logger LOGGER = LoggerFactory.getLogger(RtspServer.class);
   private static final API API;

   private final GMainContext mainCtx;
   private MainLoop mainLoop = null;
   private RtspServerObject theServer = null;
   private Thread mainLoopThread = null;
   private int attachId = -1;

   private final List<String> mountPoints = new ArrayList<>();

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

   private static class FeedDef {
      @SuppressWarnings("unused") // this is potentially serialized to a file.
      public final String name;
      public String rtspPath = null;
      public String filePath = null;

      public FeedDef(final String name) {
         this.name = name;
      }
   }

   private static enum EntryType {
      feed, path
   }

   private static RtspServer mainServer = null;

   public static void main(final String[] args) throws IOException {
      System.out.println(Arrays.toString(args));

      if(args.length != 1) {
         System.out.println("usage: java -cp [] " + RtspServer.class.getName() + " /path/to/rtsp-server.properties");
         System.exit(1);
      }

      final Properties props = new Properties();
      try (final InputStream is = new BufferedInputStream(new FileInputStream(args[0]))) {
         props.load(is);
      }

      final Map<String, FeedDef> feeds = new HashMap<>();
      props.forEach((k, v) -> {
         final String key = (String)k;
         final String value = (String)v;

         // they key needs to be of the form name.[feed|path]=value
         final int dotIndex = key.indexOf('.');
         if(dotIndex <= 0)
            throw new IllegalArgumentException("The form of the entries in the properties file need to be \"[name].feed|path=[value]\". The entry \"" + key
                  + "=" + value + "\" doesn't match this form.");
         final String name = key.substring(0, dotIndex);
         final EntryType type = EntryType.valueOf(key.substring(dotIndex + 1));

         final FeedDef fd = feeds.computeIfAbsent(name, n -> new FeedDef(n));
         if(type == EntryType.feed)
            fd.rtspPath = value;
         else
            fd.filePath = value;
      });

      try (final GstScope main = new GstScope();) {
         mainServer = new RtspServer();
         feeds.values().forEach(fd -> {
            // if the file is a relative path then we need to construct the
            // absolute path uri. Relative path file uris don't seem to be
            // interpreted as I expected by gstreamer.
            final String filePath;
            if(fd.filePath.startsWith("/"))
               filePath = "file://" + fd.filePath;
            else
               filePath = "file://" + new File(fd.filePath).getAbsolutePath();

            mainServer.play(filePath, fd.rtspPath);
         });
         mainServer.startServer(USE_DEFAULT_PORT);
         Gst.main();
      }
   }

   public static void stopMain() {
      mainServer.close();
      Gst.quit();
   }

   public int getPort() {
      return API.gst_rtsp_server_get_bound_port(getServer().getNativeAddress());
   }

   public void startServer(final int port) {
      if(!isRunning()) {
         mainLoop = new MainLoop(mainCtx);
         final RtspServerObject server = getServer();
         if(port >= 0)
            API.gst_rtsp_server_set_service(server.getNativeAddress(), Integer.toString(port));

         attachId = API.gst_rtsp_server_attach(server.getNativeAddress(), mainCtx.getNativeAddress());

         mainLoopThread = new Thread(() -> {
            try {
               LOGGER.debug("Starting RtspServer MainLoop.");
               mainLoop.run();
            } catch(final RuntimeException rte) {
               LOGGER.error("Exception thrown in main loop.", rte);
               throw rte;
            }
         }, "RtspServer-MainLoop");
         mainLoopThread.start();
      } else
         throw new IllegalStateException("RtspServer is already running.");
   }

   public void play(final String uriToSource, final String rtspPath) {
      LOGGER.trace("Will attempt to play \"{}\" at the rtsp path \"{}\"", uriToSource, rtspPath);
      try (final GstWrap<RtspServerObject> mounts = new GstWrap<>(new RtspServerObject(API.gst_rtsp_server_get_mount_points(getServer().getNativeAddress())));
            final GstWrap<RtspServerObject> factory = new GstWrap<>(new RtspServerObject(API.gst_rtsp_media_factory_new()));) {
         API.gst_rtsp_media_factory_set_launch(factory.obj.getNativeAddress(),
               "( uridecodebin uri=\"" + uriToSource + "\" ! videoconvert ! video/x-raw,format=I420 ! x264enc ! rtph264pay pt=96 name=pay0 )");
         API.gst_rtsp_media_factory_set_shared(factory.obj.getNativeAddress(), true);

         // slashify pathGLIB_EX_API
         final String apath = rtspPath.startsWith("/") ? rtspPath : ("/" + rtspPath);
         API.gst_rtsp_mount_points_add_factory(mounts.obj.getNativeAddress(),
               apath, factory.obj.getNativeAddress());

         mountPoints.add(apath);
      }
   }

   public boolean isRunning() {
      return mainLoopThread != null && mainLoopThread.isAlive();
   }

   @Override
   public void close() {
      if(mainLoop.isRunning())
         mainLoop.quit();

      ignore(() -> mainLoopThread.join(1000)); // wait a full second

      if(mainLoopThread.isAlive())
         LOGGER.warn("Never shut down main loop for RtspServer.");

      // shut down the media factory to prevent new connections
      try (final GstWrap<RtspServerObject> mounts = new GstWrap<>(
            new RtspServerObject(API.gst_rtsp_server_get_mount_points(getServer().getNativeAddress())));) {
         mountPoints.forEach(m -> API.gst_rtsp_mount_points_remove_factory(mounts.obj.getNativeAddress(), m));
      }

      API.gst_rtsp_server_client_filter(theServer.getNativeAddress(), (server, client, userData) -> {
         LOGGER.trace("Stopping :{}", client);
         return GST_RTSP_FILTER_REMOVE;
      }, Pointer.NULL);

      GlibAPI.GLIB_API.g_source_remove(attachId);
   }

   private synchronized RtspServerObject getServer() {
      if(theServer == null)
         theServer = new RtspServerObject(API.gst_rtsp_server_new());
      return theServer;
   }

   private static interface API extends Library {
      // GstRTSPServer functions
      Pointer gst_rtsp_server_new();

      int gst_rtsp_server_attach(Pointer server, Pointer context);

      void gst_rtsp_server_set_service(Pointer server, String address);

      int gst_rtsp_server_get_bound_port(Pointer server);

      @FunctionalInterface
      static public interface GstRTSPServerClientFilterFunc extends Callback {
         public int client_filter(Pointer server, Pointer client, Pointer userData);
      }

      Pointer gst_rtsp_server_client_filter(Pointer server, GstRTSPServerClientFilterFunc filter, Pointer userData);

      // GstRTSPMediaFactory functions
      Pointer gst_rtsp_media_factory_new();

      void gst_rtsp_media_factory_set_shared(Pointer factory, boolean val);

      boolean gst_rtsp_media_factory_is_shared(Pointer factory);

      void gst_rtsp_media_factory_set_launch(Pointer factory, String launch);

      // GstRTSPMountPoints functions
      Pointer gst_rtsp_server_get_mount_points(Pointer server);

      void gst_rtsp_mount_points_add_factory(final Pointer mounts, final String path, Pointer factory);

      void gst_rtsp_mount_points_remove_factory(Pointer mounts, String path);
   }

   public static interface GlibExtentionAPI extends GlibAPI {

      boolean g_socket_close(Pointer socket, Pointer ptrError);

   }
}
