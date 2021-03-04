package ai.kognition.pilecv4j.gstreamer.internal;

import com.sun.jna.Callback;
import com.sun.jna.Native;

public class BreakoutAPIRaw {

    static void init() {}

    static {
        Native.register(BreakoutAPI.LIBNAME);
    }

    public native static void set_im_maker(long immakerRef);

    public static interface push_frame_callback extends Callback {
        void push_frame(long val);
    }

    public native static void set_push_frame_callback(long me, push_frame_callback func, int needsToBeWritable);
}
