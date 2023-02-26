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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.opencv.core.Mat;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;

public class TestFunctionCall extends BaseTest {

    @Test
    public void testSimpleReturn() throws Exception {
        try(final PythonHandle pt = new PythonHandle();) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");

            try(final var res = pt.runPythonFunction("testFunction3", "simple", ParamBlock.builder()
                .arg(4));) {

                assertTrue(res.parse() instanceof Long);
                assertEquals(4, res.longValue());
                assertEquals(4, res.intValue());
                assertEquals((byte)4, res.byteValue());
                assertEquals((short)4, res.shortValue());
                assertEquals(4, res.doubleValue(), 0.0);
                assertEquals(4, res.floatValue(), 0.0);
            }

            try(final var res = pt.runPythonFunction("testFunction3", "simple", ParamBlock.builder()
                .arg(4.7));) {

                assertTrue(res.parse() instanceof Double);
                assertEquals(4.7, res.doubleValue(), 0.0);
                assertEquals(4.7f, res.floatValue(), 0.0);
            }

            try(final var res = pt.runPythonFunction("testFunction3", "simple", ParamBlock.builder()
                .arg("Hello World"));) {

                assertTrue(res.parse() instanceof String);
                assertEquals("Hello World", res.parse());
            }

            try(final CvMat mat = ImageFile.readMatFromFile(TEST_IMAGE);
                final var res = pt.runPythonFunction("testFunction3", "simple", ParamBlock.builder()
                    .arg(mat));) {
                assertTrue(res.parse() instanceof Mat);
                try(CvMat retMat = res.asMat();) {
                    System.out.println(retMat);
                }
            }
        }
    }

    @Test
    public void testMatPassPerformance() throws Exception {
        try(final PythonHandle pt = new PythonHandle();) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");
            final int NUM_ITERS = 100000;
            try(final CvMat mat = ImageFile.readMatFromFile(TEST_IMAGE);) {

                final long startTime = System.currentTimeMillis();
                for(int i = 0; i < NUM_ITERS; i++) {
                    try(final var res = pt.runPythonFunction("testReceiveMat", "simple", ParamBlock.builder()
                        .arg(mat));) {
                        assertTrue(res.parse() instanceof Mat);
                        try(CvMat retMat = res.asMat();) {
//                            System.out.println(retMat);
                        }
                    }
                }
                final double duration = System.currentTimeMillis() - startTime;
                System.out
                    .println(
                        NUM_ITERS + " passes in " + String.format("%.2f", duration / 1000) + " or " + String.format("%.2f", (NUM_ITERS * 1000.0) / duration)
                            + " per second");
            }
        }
    }

    @Test
    public void testPyObjectReturn() throws Exception {
        try(final PythonHandle pt = new PythonHandle();) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");

            PyObject toLive = null;
            try(final var res = pt.runPythonFunction("testFunction4", "simple", ParamBlock.builder());) {
                assertTrue(res.parse() instanceof PyObject);

                toLive = res.asPyObject();
            }

            try(PyObject qc = toLive;
                var qc1 = pt.runPythonFunction("testFunction5", "receive", ParamBlock.builder()
                    .arg(toLive));) {

                assertNull(qc1);
            }
        }
    }

    @Test
    public void testPassList() throws Exception {
        try(final PythonHandle pt = new PythonHandle();) {

            // add the module path
            pt.addModulePath("./src/test/resources/python");

            try(final var res = pt.runPythonFunction("testFunction3", "simple", ParamBlock.builder()
                .arg(List.of("Hello", 5, 5.5))

            );) {
                assertTrue(res.parse() instanceof List);
                System.out.println(res.parse());
            }
        }
    }
}
