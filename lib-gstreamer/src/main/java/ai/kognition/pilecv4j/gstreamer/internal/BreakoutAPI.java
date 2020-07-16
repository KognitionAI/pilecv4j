package ai.kognition.pilecv4j.gstreamer.internal;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Registry;
import org.freedesktop.gstreamer.lowlevel.GFunctionMapper;
import org.freedesktop.gstreamer.lowlevel.GTypeMapper;
import org.freedesktop.gstreamer.lowlevel.annotations.CallerOwnsReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.gstreamer.BreakoutFilter;
import ai.kognition.pilecv4j.gstreamer.GstScope;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;
import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public interface BreakoutAPI extends Library {
    public static final Logger LOGGER = LoggerFactory.getLogger(BreakoutAPI.class);

    public static final String LIBNAME = "gstbreakout";

    public static final AtomicBoolean inited = new AtomicBoolean(false);

    public static BreakoutAPI _init() {
        if(inited.get())
            throw new IllegalStateException("Cannot initialize the Gstreamer Breakout filter twice.");

        // If we get here but we haven't already initialized Gst itself, then the
        // setting of the Registry path will be ignored and we wont be able
        // to find our plugin.
        if(!Gst.isInitialized())
            throw new IllegalStateException("You must inititialize Gst prior to referencing the " + BreakoutFilter.class.getSimpleName() + ". You can use "
                + GstScope.class.getSimpleName() + " to do this.");

        CvMat.initOpenCv();

        if(!inited.getAndSet(true)) {
            NativeLibraryLoader.loader()
                .library(LIBNAME)
                .destinationDir(new File(System.getProperty("java.io.tmpdir"), LIBNAME).getAbsolutePath())
                .addCallback((dir, libname, oslibname) -> {
                    LOGGER.info("scanning dir:{}, libname:{}, oslibname:{}", dir, libname, oslibname);
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                    Registry.get().scanPath(dir.getAbsolutePath());
                })
                .load();
        }

        final Map<String, Object> options = new HashMap<String, Object>();
        options.put(Library.OPTION_TYPE_MAPPER, new GTypeMapper());
        options.put(Library.OPTION_FUNCTION_MAPPER, new GFunctionMapper());

        final BreakoutAPI ret = Native.loadLibrary(LIBNAME, BreakoutAPI.class, options);

        BreakoutAPIRaw.init();

        BreakoutAPIRaw.set_im_maker(ImageAPI.get_im_maker()); // the bridge

        return ret;
    }

    static BreakoutAPI FILTER_API = _init();

    long who_am_i(BreakoutFilter filter);

    boolean gst_breakout_set_caps(BreakoutFilter appsrc, Caps incaps, Caps outcaps);

    @CallerOwnsReturn
    Buffer gst_breakout_current_frame_buffer(BreakoutFilter breakout);

    @CallerOwnsReturn
    Caps gst_breakout_current_frame_caps(BreakoutFilter breakout);

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
