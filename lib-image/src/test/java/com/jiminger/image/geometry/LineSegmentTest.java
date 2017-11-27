package com.jiminger.image.geometry;

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
