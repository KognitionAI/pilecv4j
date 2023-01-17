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

package ai.kognition.pilecv4j.ffmpeg;

import java.io.File;
import java.net.URI;

import net.dempsy.vfs.Vfs;

public class BaseTest {
    public static final int MEG = 1024 * 1024;

    public static final boolean SHOW;

    static {
        final String sysOpSHOW = System.getProperty("pilecv4j.SHOW");
        final boolean sysOpSet = sysOpSHOW != null;
        boolean show = ("".equals(sysOpSHOW) || Boolean.parseBoolean(sysOpSHOW));
        if(!sysOpSet)
            show = Boolean.parseBoolean(System.getenv("PILECV4J_SHOW"));
        SHOW = show;
    }

    public final static File STREAM_FILE;

    static {
        try(var vfs = new Vfs();) {
            // STREAM_FILE = vfs.toFile(new URI("classpath:///test-videos/Libertas-70sec.mp4"));
//            STREAM_FILE = vfs.toFile(new URI("file:///tmp/test-videos/Libertas-70sec.h265.mp4"));
            STREAM_FILE = vfs.toFile(new URI("file:///tmp/test-videos/MemberEntranceClip.1080.mp4"));

            if(!STREAM_FILE.exists())
                throw new RuntimeException();
        } catch(final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public final static URI STREAM = STREAM_FILE.toURI();
}
