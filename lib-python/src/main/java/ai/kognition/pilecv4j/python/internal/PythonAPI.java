package ai.kognition.pilecv4j.python.internal;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public class PythonAPI {
    public static final String LIBNAME = "pilecv4jpython";

    public static void _init() {}

    // needs to match LogLevel enum in the C++ code.
    public static final int LOG_LEVEL_TRACE = 0;
    public static final int LOG_LEVEL_DEBUG = 1;
    public static final int LOG_LEVEL_INFO = 2;
    public static final int LOG_LEVEL_WARN = 3;
    public static final int LOG_LEVEL_ERROR = 4;
    public static final int LOG_LEVEL_FATAL = 5;

    static {
    	CvMat.initOpenCv();
    	
        NativeLibraryLoader.loader()
            .library(LIBNAME)
            .addCallback((dir, libname, oslibname) -> {
                if(LIBNAME.equals(libname))
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();
        Native.register(LIBNAME);
    }

    public static interface get_image_source extends Callback {
        public long image_source(long ptRef);
    }

    public static native int initPython();

    public static native void addModulePath(String absDir);

    public static native int runPythonFunction(String module, String function, long dictRef);

    public static native long imageSourceSend(long imageSourceRef, long matRef, int rgb);

    public static native long imageSourcePeek(long imageSourceRef);

    public static native void imageSourceClose(long imageSourceRef);

    public static native void kogMatResults_close(long nativeObj);

    public static native long kogMatResults_getResults(long nativeObj);

    public static native int kogMatResults_hasResult(long nativeObj);

    public static native int kogMatResults_isAbandoned(long nativeObj);

    public static native Pointer statusMessage(int status);

    public static native void freeStatusMessage(Pointer pointer);

    public static native long newParamDict();

    public static native void closeParamDict(long dictRef);

    public static native int putStringParamDict(long dictRef, String key, String valRaw);

    public static native int putIntParamDict(long dictRef, String key, long valRaw);

    public static native int putFloatParamDict(long dictRef, String key, double valRaw);

    public static native int putPytorchParamDict(long dictRef, String key, long nativeObj);

    public static native int putBooleanParamDict(long dict, String key, int i);

    public static native int setLogLevel(int logLevelSet);

    public static native long makeImageSource(long ptRef);

    public static native long initKogSys(get_image_source cb);

    public static native int kogSys_numModelLabels(long ptRef);

    public static native Pointer kogSys_modelLabel(final long ptRef, final int index);
}
