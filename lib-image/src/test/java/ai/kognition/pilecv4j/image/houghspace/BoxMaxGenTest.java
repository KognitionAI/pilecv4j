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

package ai.kognition.pilecv4j.image.houghspace;

import java.util.Arrays;

import org.junit.Test;

import ai.kognition.pilecv4j.image.geometry.LineSegment;
import ai.kognition.pilecv4j.image.geometry.SimplePoint;
import ai.kognition.pilecv4j.image.houghspace.internal.Mask;

public class BoxMaxGenTest {

    @Test
    public void testBoxAsModel() throws Exception {

        final Model m = new SegmentModel(Arrays.asList(
            new LineSegment(new SimplePoint(0, 0), new SimplePoint(0, 49.0)),
            new LineSegment(new SimplePoint(0.0, 49.0), new SimplePoint(49.0, 49.0)),
            new LineSegment(new SimplePoint(49.0, 49.0), new SimplePoint(49.0, 0)),
            new LineSegment(new SimplePoint(49.0, 0), new SimplePoint(0, 0))));

        final Mask mask = Mask.generateMask(m, 1.0, 1.0);
        System.out.println(mask);
    }

}
