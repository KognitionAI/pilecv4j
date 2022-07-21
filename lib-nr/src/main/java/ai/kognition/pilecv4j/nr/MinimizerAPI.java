package ai.kognition.pilecv4j.nr;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public class MinimizerAPI {

    public static final String LIBNAME = "ai.kognition.pilecv4j.util";

    static {
        NativeLibraryLoader.loader()
            .library(LIBNAME)
            .addPreLoadCallback((dir, libname, oslibname) -> {
                NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();

        Native.register(LIBNAME);
    }

    static void _init() {}

    public interface EvalCallback extends Callback {
        float eval(Pointer floatArrayX, Pointer status);
    }

    public static native double pilecv4j_image_dominimize(EvalCallback func, int n, double[] pd, double[] xi, double jftol, double[] minVal, int[] status);

    public static native Pointer pilecv4j_image_nrGetErrorMessage();
}
