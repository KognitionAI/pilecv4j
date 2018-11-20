package ai.kognition.pilecv4j.gstreamer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Registry;
import org.freedesktop.gstreamer.lowlevel.GFunctionMapper;
import org.freedesktop.gstreamer.lowlevel.GTypeMapper;
import org.freedesktop.gstreamer.lowlevel.annotations.CallerOwnsReturn;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public interface BreakoutAPI extends Library {

   public static final String LIBNAME = "gstbreakout";

   public static final AtomicBoolean inited = new AtomicBoolean(false);

   public static BreakoutAPI _init() {
      if(!inited.getAndSet(true)) {
         NativeLibraryLoader.loader()
               .library(LIBNAME)
               .addCallback((dir, libname, oslibname) -> {
                  NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                  Registry.get().scanPath(dir.getAbsolutePath());
               })
               .load();
      }

      final Map<String, Object> options = new HashMap<String, Object>();
      options.put(Library.OPTION_TYPE_MAPPER, new GTypeMapper());
      options.put(Library.OPTION_FUNCTION_MAPPER, new GFunctionMapper());

      return Native.loadLibrary(LIBNAME, BreakoutAPI.class, options);
   }

   static BreakoutAPI FILTER_API = _init();

   boolean gst_breakout_set_caps(BreakoutFilter appsrc, Caps incaps, Caps outcaps);

   @CallerOwnsReturn
   Buffer gst_breakout_current_frame_buffer(BreakoutFilter breakout);

   @CallerOwnsReturn
   Caps gst_breakout_current_frame_caps(BreakoutFilter breakout);

   int gst_breakout_current_frame_width(BreakoutFilter breakout);

   int gst_breakout_current_frame_height(BreakoutFilter breakout);

   @CallerOwnsReturn
   void gst_breakout_current_frame_details(BreakoutFilter breakout, _FrameDetails details);

   public static class _FrameDetails extends com.sun.jna.Structure {
      public Pointer buffer;
      public Pointer caps;
      public int width;
      public int height;

      @Override
      protected List<String> getFieldOrder() {
         return frameDetailsFieldOrder;
      }
   }

   public static final List<String> frameDetailsFieldOrder = getFrameDetailsFieldOrder();

   public static List<String> getFrameDetailsFieldOrder() {
      try {
         return Arrays.asList(_FrameDetails.class.getField("buffer").getName(),
               _FrameDetails.class.getField("caps").getName(),
               _FrameDetails.class.getField("width").getName(),
               _FrameDetails.class.getField("height").getName());
      } catch(NoSuchFieldException | SecurityException e) {
         // This will only happen if the structure changes and should cause systemic
         // test failures pointing to that fact.
         throw new RuntimeException(e);
      }
   }

}
