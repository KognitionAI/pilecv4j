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
