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

package ai.kognition.pilecv4j.image.geometry;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LineSegmentTest {

    @Test
    public void simpleDistanceTestXAxis() {
        final LineSegment seg = new LineSegment(new SimplePoint(0.0, 0.0), new SimplePoint(0.0, 1.0));

        assertEquals(1.0, seg.distance(new SimplePoint(1.0, 0.5)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(-1.0, 0.5)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(1.0, 1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(1.0, 0.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(-1.0, 1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(-1.0, 0.0)), 0.0000001);

        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(1.0, 2.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, 2.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(1.0, -1.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, -1.0)), 0.0000001);
    }

    @Test
    public void simpleDistanceTestYAxis() {
        final LineSegment seg = new LineSegment(new SimplePoint(0.0, 0.0), new SimplePoint(1.0, 0.0));

        assertEquals(1.0, seg.distance(new SimplePoint(0.5, 1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(0.5, -1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(1.0, 1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(0.0, 1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(1.0, -1.0)), 0.0000001);
        assertEquals(1.0, seg.distance(new SimplePoint(0.0, -1.0)), 0.0000001);

        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(2.0, 1.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(2.0, -1.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, 1.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, -1.0)), 0.0000001);
    }

    @Test
    public void simpleDistanceTest1P1() {
        final LineSegment seg = new LineSegment(new SimplePoint(0.0, 0.0), new SimplePoint(1.0, 1.0));

        assertEquals(Math.sqrt(2.0) / 2.0, seg.distance(new SimplePoint(1.0, 0.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0) / 2.0, seg.distance(new SimplePoint(0.0, 1.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(2.0, 0.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(1.0, -1.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(0.0, 2.0)), 0.0000001);
        assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, -1.0)), 0.0000001);

        // assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(2.0, 1.0)), 0.0000001);
        // assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(2.0, -1.0)), 0.0000001);
        // assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, 1.0)), 0.0000001);
        // assertEquals(Math.sqrt(2.0), seg.distance(new SimplePoint(-1.0, -1.0)), 0.0000001);
    }

}
