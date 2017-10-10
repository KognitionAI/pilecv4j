package com.jiminger.image.houghspace;

import java.util.Collection;

import com.jiminger.image.geometry.LineSegment;

public class SegmentModel implements Model {

    final private Collection<LineSegment> segments;

    public SegmentModel(final Collection<LineSegment> segments) {
        this.segments = segments;
    }

    @Override
    public double distance(final double ox, final double oy, final double theta, final double scale) {
        // TODO Auto-generated method stub
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
