package com.jiminger.nr;

import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import net.dempsy.util.library.NativeLibraryLoader;

public interface MinimizerAPI extends Library {

    public static final String LIBNAME = "utilities.jiminger.com";

    static final AtomicBoolean inited = new AtomicBoolean(false);

    public static String _init() {
        if (!inited.getAndSet(true)) {
            NativeLibraryLoader.loader()
                    .library(LIBNAME)
                    .addCallback((dir, libname, oslibname) -> {
                        NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                    })
                    .load();
        }
        return LIBNAME;
    }

    static MinimizerAPI API = Native.loadLibrary(_init(), MinimizerAPI.class);

    public interface EvalCallback extends Callback {
        float eval(Pointer floatArrayX, Pointer status);
    }

    double dominimize(EvalCallback func, int n, double[] pd, double[] xi, double jftol, double[] minVal, int[] status);

    // caller owns return
    Pointer nrGetErrorMessage();
}
