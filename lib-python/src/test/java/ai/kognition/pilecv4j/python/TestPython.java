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

import static ai.kognition.pilecv4j.python.UtilsForTesting.translateClasspath;
import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import net.dempsy.utils.test.ConditionPoll;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2;
import ai.kognition.pilecv4j.ffmpeg.Ffmpeg2.StreamContext;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.CvRaster.Closer;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.python.KogSys.KogMatResults;

public class TestPython {
    public static final boolean SHOW;

    static {
        final String sysOpSHOW = System.getProperty("pilecv4j.SHOW");
        final boolean sysOpSet = sysOpSHOW != null;
        boolean show = ("".equals(sysOpSHOW) || Boolean.parseBoolean(sysOpSHOW));
        if(!sysOpSet)
            show = Boolean.parseBoolean(System.getenv("PILECV4J_SHOW"));
        SHOW = show;
    }

    @Test
    public void testModel() throws Exception {
        final String testImageFilename = translateClasspath("test-data/people.jpeg");

        final int NUM_IMAGE_PASS = 1000;

        final AtomicBoolean failed = new AtomicBoolean(false);

        try(final Closer closer = new Closer();
            final KogSys pt = new KogSys();
            final CvMat mat = ImageFile.readMatFromFile(testImageFilename);) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");

            final Map<String, Object> params = new HashMap<>();
            params.put("kogsys", pt);

            final Thread thread = chain(
                new Thread(() -> {
                    try {
                        pt.runPythonFunction("testFunction", "func", params);
                    } catch(final RuntimeException rte) {
                        failed.set(true);
                        rte.printStackTrace();
                        throw rte;
                    }
                }, "Python Thread"),
                t -> t.setDaemon(true),
                t -> t.start());

            // wait for the script to get an image source
            assertTrue(ConditionPoll.poll(o -> pt.sourceIsInitialized()));

            final long startTime = System.currentTimeMillis();

            KogMatResults prev = null;

            for(int count = 0; count < NUM_IMAGE_PASS && !failed.get(); count++) {

                try(CvMat toSend = CvMat.shallowCopy(mat);) {

                    // make sure the previous (if there is one) is gone
                    while(pt.imageSource.peek() != 0L) // waits until the current one is gone.
                        Thread.yield();

                    final KogMatResults results = pt.sendMat(toSend, false);
                    if(prev != null) {
                        final KogMatResults fprev = prev;
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
            assertTrue(ConditionPoll.poll(o -> !thread.isAlive()));
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
        try(final KogSys pt = new KogSys();
            final ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null;
            final StreamContext c = Ffmpeg2.createStreamContext();) {

            pt.addModulePath("./src/test/resources/python");

            final Map<String, Object> params = new HashMap<>();
            params.put("kogsys", pt);

            final Thread thread = chain(
                new Thread(() -> {
                    try {
                        pt.runPythonFunction("testFunction2", "func", params);
                    } catch(final RuntimeException rte) {
                        failed.set(true);
                        rte.printStackTrace();
                        throw rte;
                    }
                }, "Python Thread"),
                t -> t.setDaemon(true),
                t -> t.start());

            // wait for the script to get an image source
            assertTrue(ConditionPoll.poll(o -> pt.sourceIsInitialized()));

            c
                .addOption("rtsp_flags", "prefer_tcp")
                .createMediaDataSource(STREAM)
                .openChain()
                .createFirstVideoStreamSelector()
                .createVideoFrameProcessor(f -> {
                    frameCount.getAndIncrement();

                    try(final KogMatResults results = pt.sendMat(f, true);) {
                        assertTrue(uncheck(() -> ConditionPoll.poll(o -> results.hasResult())));

                        try(CvMat result = results.getResultMat();) {
                            assertNotNull(result);
                            if(SHOW) {
                                id.update(result);
                            }
                        }
                    }
                })
                .streamContext()
                .sync()
                .play();

            pt.eos(); // EOS
            assertTrue(ConditionPoll.poll(o -> !thread.isAlive()));
            assertNotNull(pt.imageSource);
            assertEquals(0L, pt.imageSource.peek());

        }
        assertFalse(failed.get());
        assertTrue(frameCount.get() > 50);
    }

}
