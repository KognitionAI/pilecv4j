package com.jiminger.image.houghspace;

import java.util.Collection;

import com.jiminger.image.geometry.LineSegment;
import com.jiminger.image.geometry.SimplePoint;

public class SegmentModel implements Model {

    final private LineSegment[] segments;
    final private int num;

    public SegmentModel(final Collection<LineSegment> segments) {
        this.segments = segments.stream().toArray(LineSegment[]::new);
        this.num = this.segments.length;
    }

    @Override
    public double distance(final double ox, final double oy, final double scale) {
        final double minDist = Double.NEGATIVE_INFINITY;
        final int minIndex = -1;
        for (int i = 0; i < num; i++) {
            final LineSegment seg = segments[i];
            final double dist = seg.distance(new SimplePoint(oy, ox));
        }
        return 0;
    }

    @Override
    public short gradient(final double ox, final double oy) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double featureWidth() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double featureHeight() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean flipYAxis() {
        // TODO Auto-generated method stub
        return false;
    }

}
