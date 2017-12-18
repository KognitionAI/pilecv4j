package com.jiminger.image.houghspace;

import java.util.Arrays;

import org.junit.Test;

import com.jiminger.image.geometry.LineSegment;
import com.jiminger.image.geometry.SimplePoint;
import com.jiminger.image.houghspace.internal.Mask;

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
