/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            .addPreLoadCallback((dir, libname, oslibname) -> {
                if(LIBNAME.equals(libname))
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();
        Native.register(LIBNAME);
    }

    public static interface get_image_source extends Callback {
        public long image_source(long ptRef);
    }

    public static native int pilecv4j_python_initPython();

    public static native void pilecv4j_python_addModulePath(String absDir);

    public static native int pilecv4j_python_runPythonFunction(String module, String function, long dictRef);

    public static native long pilecv4j_python_imageSourceSend(long imageSourceRef, long matRef, int rgb);

    public static native long pilecv4j_python_imageSourcePeek(long imageSourceRef);

    public static native void pilecv4j_python_imageSourceClose(long imageSourceRef);

    public static native void pilecv4j_python_kogMatResults_close(long nativeObj);

    public static native long pilecv4j_python_kogMatResults_getResults(long nativeObj);

    public static native int pilecv4j_python_kogMatResults_hasResult(long nativeObj);

    public static native int pilecv4j_python_kogMatResults_isAbandoned(long nativeObj);

    public static native Pointer pilecv4j_python_statusMessage(int status);

    public static native void pilecv4j_python_freeStatusMessage(Pointer pointer);

    public static native long pilecv4j_python_newParamDict();

    public static native void pilecv4j_python_closeParamDict(long dictRef);

    public static native int pilecv4j_python_putStringParamDict(long dictRef, String key, String valRaw);

    public static native int pilecv4j_python_putIntParamDict(long dictRef, String key, long valRaw);

    public static native int pilecv4j_python_putFloatParamDict(long dictRef, String key, double valRaw);

    public static native int pilecv4j_python_putPytorchParamDict(long dictRef, String key, long nativeObj);

    public static native int pilecv4j_python_putBooleanParamDict(long dict, String key, int i);

    public static native int pilecv4j_python_setLogLevel(int logLevelSet);

    public static native long pilecv4j_python_makeImageSource(long ptRef);

    public static native long pilecv4j_python_initKogSys(get_image_source cb);

    public static native int pilecv4j_python_kogSys_numModelLabels(long ptRef);

    public static native Pointer pilecv4j_python_kogSys_modelLabel(final long ptRef, final int index);
}
