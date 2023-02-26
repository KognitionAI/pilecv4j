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

package ai.kognition.pilecv4j.python;

import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import net.dempsy.utils.test.ConditionPoll;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.MediaContext;
import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.python.PythonHandle.PythonResults;

public class TestPython extends BaseTest {

    @Test
    public void testModel() throws Exception {

        final int NUM_IMAGE_PASS = 1000;

        final AtomicBoolean failed = new AtomicBoolean(false);

        try(final Closer closer = new Closer();
            final PythonHandle pt = new PythonHandle();
            final CvMat mat = ImageFile.readMatFromFile(TEST_IMAGE);) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");

            final var res = pt.runPythonFunctionAsynch("testFunction", "func", ParamBlock.builder()
                .arg("kogsys", pt));

            // wait for the script to get an image source
            assertTrue(ConditionPoll.poll(o -> res.sourceIsInitialized()));

            final long startTime = System.currentTimeMillis();

            PythonResults prev = null;
            for(int count = 0; count < NUM_IMAGE_PASS && !failed.get(); count++) {

                try(CvMat toSend = CvMat.shallowCopy(mat);) {

                    // make sure the previous (if there is one) is gone
                    while(pt.imageSource.peek() != 0L) // waits until the current one is gone.
                        Thread.yield();

                    final PythonResults results = pt.sendMat(toSend, false, null);
                    if(prev != null) {
                        final PythonResults fprev = prev;
                        assertTrue(ConditionPoll.poll(o -> fprev.hasResult()));
                        assertTrue(prev.hasResult());
                        try(final CvMat resMat = prev.getResultMat();) {
                            assertNotNull(resMat);
                        }
                    }
                    if(prev != null)
                        prev.close();
                    prev = results;
                }
            }
            if(prev != null)
                prev.close();

            final long endTime = System.currentTimeMillis();
            System.out.println("Rate: " + ((double)(NUM_IMAGE_PASS * 1000) / (endTime - startTime)) + " det/sec");

            assertFalse(failed.get());
            pt.eos(); // EOS
            assertTrue(ConditionPoll.poll(o -> !res.thread.isAlive()));
            assertNotNull(pt.imageSource);
            assertEquals(0L, pt.imageSource.peek());
        }
    }

    @Test
    public void testImagePassingPerformance() throws Exception {

        final int NUM_IMAGE_PASS = 1000;

        final AtomicBoolean failed = new AtomicBoolean(false);

        try(final PythonHandle pt = new PythonHandle();
            final CvMat mat = ImageFile.readMatFromFile(TEST_IMAGE);) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");

            final var res = pt.runPythonFunctionAsynch("testReceiveMat2", "func", ParamBlock.builder()
                .arg("kogsys", pt));

            // wait for the script to get an image source
            assertTrue(ConditionPoll.poll(o -> res.sourceIsInitialized()));

            final long startTime = System.currentTimeMillis();

            for(int count = 0; count < NUM_IMAGE_PASS && !failed.get(); count++) {
                try(CvMat toSend = CvMat.shallowCopy(mat);) {
                    final PythonResults results = pt.sendMat(toSend, false, null);
                    while(!results.hasResult());
                    try(final CvMat resMat = results.getResultMat();) {
                        assertNotNull(resMat);
                    }
                }
            }

            final long endTime = System.currentTimeMillis();
            System.out.println("Rate: " + ((double)(NUM_IMAGE_PASS * 1000) / (endTime - startTime)) + " det/sec");

            assertFalse(failed.get());
            pt.eos(); // EOS
            assertTrue(ConditionPoll.poll(o -> !res.thread.isAlive()));
            assertNotNull(pt.imageSource);
            assertEquals(0L, pt.imageSource.peek());
        }
    }

    @Test
    public void testConsumeFrames() throws Exception {
        final File STREAM_FILE = new File(TestPython.class.getClassLoader().getResource("test-data/Libertas-70sec.mp4").getFile());
        final URI STREAM = STREAM_FILE.toURI();

        final AtomicBoolean failed = new AtomicBoolean(false);

        final AtomicLong frameCount = new AtomicLong(0);
        try(final PythonHandle pt = new PythonHandle();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;
            final MediaContext c = Ffmpeg.createMediaContext();) {

            pt.addModulePath("./src/test/resources/python");

            final var res = pt.runPythonFunctionAsynch("testFunction2", "func", ParamBlock.builder().arg(pt));

            // wait for the script to get an image source
            assertTrue(ConditionPoll.poll(o -> res.sourceIsInitialized()));

            c
                .addOption("rtsp_flags", "prefer_tcp")
                .source(STREAM)
                .chain("default")
                .selectFirstVideoStream()
                .processVideoFrames(f -> {
                    frameCount.getAndIncrement();

                    try(final PythonResults results = pt.sendMat(f, true, fakeParams);) {
                        assertTrue(uncheck(() -> ConditionPoll.poll(o -> results.hasResult())));

                        try(CvMat result = results.getResultMat();) {
                            assertNotNull(result);
                            if(SHOW) {
                                id.update(result);
                            }
                        }
                    }
                })
                .mediaContext()
                // .sync()
                .play();

            pt.eos(); // EOS
            assertTrue(ConditionPoll.poll(o -> !res.thread.isAlive()));
            assertNotNull(pt.imageSource);
            assertEquals(0L, pt.imageSource.peek());

        }
        assertFalse(failed.get());
        assertTrue(frameCount.get() > 50);
    }

}
