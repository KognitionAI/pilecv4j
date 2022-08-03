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

package ai.kognition.pilecv4j.image.mjpeg;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestMJPEG {

    final String animationDir = new File(
        getClass().getClassLoader().getResource("animation").getFile()).getAbsolutePath();

    @Rule public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testMJPEGWriter() throws Exception {
        final File aviout = tmpFolder.newFile();
        MJPEGWriter.main(new String[] {"-pdir",animationDir,"-avifile",aviout.getAbsolutePath()});
        assertTrue(aviout.exists());
    }

    @Test
    public void testMJPEGWriterDumbFailure() throws Exception {
        final File aviout = tmpFolder.newFile();
        assertFalse(MJPEGWriter.commandLine(new String[] {"-avifile",aviout.getAbsolutePath()}));
        assertFalse(MJPEGWriter.commandLine(new String[] {"-pdir","/no/directory/here","-avifile",aviout.getAbsolutePath()}));
    }

}
