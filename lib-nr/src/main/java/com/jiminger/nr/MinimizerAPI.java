package com.jiminger.nr;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import net.dempsy.util.library.NativeLibraryLoader;

public class MinimizerAPI {

   public static final String LIBNAME = "utilities.jiminger.com";

   static {
      NativeLibraryLoader.loader()
            .library(LIBNAME)
            .addCallback((dir, libname, oslibname) -> {
               NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();

      Native.register(LIBNAME);
   }

   static void _init() {}

   public interface EvalCallback extends Callback {
      float eval(Pointer floatArrayX, Pointer status);
   }

   public static native double dominimize(EvalCallback func, int n, double[] pd, double[] xi, double jftol, double[] minVal, int[] status);

   public static native Pointer nrGetErrorMessage();
}
