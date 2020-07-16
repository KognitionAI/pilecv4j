package ai.kognition.pilecv4j.gstreamer.internal;

import com.sun.jna.Native;

public class BreakoutAPIRaw {

    static void init() {}

    static {
        Native.register(BreakoutAPI.LIBNAME);
    }

    public native static long gst_breakout_current_frame_mat(long breakout, boolean writable);

    public native static void gst_breakout_current_frame_mat_unmap(long gstmat);

    public native static void set_im_maker(long immakerRef);

}
