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

package ai.kognition.pilecv4j.util;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * This class is a simple helper class that will automatically free native memory
 * pointed to by a JNA Pointer on close. It's basically a JNA Pointer guard class.
 */
public class NativePointerWrap implements AutoCloseable {

    public final Pointer ptr;

    public NativePointerWrap(final Pointer ptr) {
        this.ptr = ptr;
    }

    @Override
    public void close() {
        if(ptr != null)
            Native.free(Pointer.nativeValue(ptr));
    }
}
