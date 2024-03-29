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
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

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

    // ===================================================
    // Global python calls
    // ===================================================
    public static native int pilecv4j_python_initPython();

    public static native void pilecv4j_python_addModulePath(String absDir);

    public static native int pilecv4j_python_runPythonFunction(String module, String function, long args, long dictRef,
        PointerByReference result, IntByReference resultSize);

    public static native int pilecv4j_python_freeFunctionResults(Pointer resultBuf);

    public static native void pilecv4j_python_pyObject_decref(long nativeRef);

    public static native void pilecv4j_python_pyObject_incref(long nativeRef);

    public static native long pilecv4j_python_pyObject_PyNone(int incref);

    // ===================================================
    // KogSys lifecycle and methods
    // ===================================================
    public static native long pilecv4j_python_kogSys_create(get_image_source cb);

    public static native int pilecv4j_python_kogSys_destroy(long kogSysRef);

    public static native int pilecv4j_python_kogSys_numModelLabels(long ptRef);

    public static native Pointer pilecv4j_python_kogSys_modelLabel(final long ptRef, final int index);

    // ==============================================================
    // ImageSource lifecycle and methods
    // ==============================================================
    public static native long pilecv4j_python_imageSource_create(long ptRef);

    public static native void pilecv4j_python_imageSource_destroy(long imageSourceRef);

    public static native long pilecv4j_python_imageSource_send(long imageSourceRef, long paramsDict, long matRef, int rgb);

    public static native long pilecv4j_python_imageSource_peek(long imageSourceRef);

    // ==============================================================
    // KogMatResults lifecycle and methods
    // ==============================================================
    public static native void pilecv4j_python_kogMatResults_destroy(long nativeObj);

    public static native long pilecv4j_python_kogMatResults_getResults(long nativeObj);

    public static native int pilecv4j_python_kogMatResults_hasResult(long nativeObj);

    public static native int pilecv4j_python_kogMatResults_isAbandoned(long nativeObj);

    // ==============================================================
    // Python Tuple lifecycle and methods
    // ==============================================================
    public static native long pilecv4j_python_tuple_create(int size);

    public static native void pilecv4j_python_tuple_destroy(long tupleRef);

    public static native int pilecv4j_python_tuple_putString(long tupleRef, int index, String valRaw);

    public static native int pilecv4j_python_tuple_putMat(long tupleRef, int index, long valRef);

    public static native int pilecv4j_python_tuple_putPyObject(long tupleRef, int index, long valRef);

    public static native int pilecv4j_python_tuple_putInt(long tupleRef, int index, long valRaw);

    public static native int pilecv4j_python_tuple_putFloat(long tupleRef, int index, double valRaw);

    public static native int pilecv4j_python_tuple_putKogSys(long tupleRef, int index, long nativeObj);

    public static native int pilecv4j_python_tuple_putBoolean(long tupleRef, int index, int i);

    // ==============================================================
    // Python Tuple lifecycle and methods
    // ==============================================================
    public static native long pilecv4j_python_dict_create();

    public static native void pilecv4j_python_dict_destroy(long dictRef);

    public static native int pilecv4j_python_dict_putString(long dictRef, String key, String valRaw);

    public static native int pilecv4j_python_dict_putMat(long dictRef, String key, long valRef);

    public static native int pilecv4j_python_dict_putPyObject(long dictRef, String key, long valRef);

    public static native int pilecv4j_python_dict_putInt(long dictRef, String key, long valRaw);

    public static native int pilecv4j_python_dict_putFloat(long dictRef, String key, double valRaw);

    public static native int pilecv4j_python_dict_putKogSys(long dictRef, String key, long nativeObj);

    public static native int pilecv4j_python_dict_putBoolean(long dict, String key, int i);

    // ===================================================
    // Status/Error code access
    // ===================================================
    public static native Pointer pilecv4j_python_status_message(int status);

    public static native void pilecv4j_python_status_freeMessage(Pointer pointer);

    // ===================================================
    // Logging
    // ===================================================
    public static native int pilecv4j_python_setLogLevel(int logLevelSet);
}
