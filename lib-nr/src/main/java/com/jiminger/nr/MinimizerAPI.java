package com.jiminger.nr;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface MinimizerAPI extends Library {
    static MinimizerAPI API = Native.loadLibrary(Minimizer.LIBNAME, MinimizerAPI.class);

    public interface EvalCallback extends Callback {
        float eval(Pointer floatArrayX, Pointer status);
    }

    double dominimize(EvalCallback func, int n, double[] pd, double[] xi, double jftol, double[] minVal, int[] status);

    // caller owns return
    Pointer nrGetErrorMessage();
}
