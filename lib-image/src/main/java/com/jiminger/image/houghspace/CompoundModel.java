package com.jiminger.image.houghspace;

import java.util.Collection;

import com.jiminger.image.geometry.LineSegment;
import com.jiminger.image.geometry.Point;
import com.jiminger.image.geometry.SimplePoint;

public class CompoundModel implements Model {

    final private ModelWithOffset[] segments;
    final private double w;
    final private double h;
    final private Point offset;

    public static class ModelWithOffset {
        public final Model model;
        public final Point offset;

        public ModelWithOffset(final Model model, final Point offset) {
            this.model = model;
            this.offset = offset;
        }
    }

    public CompoundModel(final Collection<ModelWithOffset> segments) {
        if (segments == null || segments.size() == 0)
            throw new IllegalArgumentException();

        final ModelWithOffset[] array = segments.stream().toArray(ModelWithOffset[]::new);
        this.segments = array;
        // this.num = this.segments.length;

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (final ModelWithOffset p : segments) {
            final double w2 = p.model.featureWidth() / 2.0;
            final double h2 = p.model.featureHeight() / 2.0;
            final double potMinX = p.offset.x() - w2;
            final double potMaxX = p.offset.x() + w2;
            final double potMinY = p.offset.y() - h2;
            final double potMaxY = p.offset.y() + h2;
            if (potMinX < minX)
                minX = potMinX;
            if (potMaxX > maxX)
                maxX = potMaxX;
            if (potMinY < minY)
                minY = potMinY;
            if (potMaxY > maxY)
                maxY = potMaxY;
        }

        this.w = maxX - minX;
        this.h = maxY - minY;

        // offset that will move the center of the compound model to the origin.
        this.offset = new SimplePoint((maxY + minY) / 2.0, (maxX + minX) / 2.0);
    }

    @Override
    public double distance(final double ox, final double oy, final double scale) {
        final double minDist = Double.POSITIVE_INFINITY;
        // for (final LineSegment seg : segments) {
        // final double dist = seg.distance(new SimplePoint(oy, ox));
        // if (dist < minDist)
        // minDist = dist;
        // }
        return minDist;
    }

    @Override
    public byte gradientDirection(final double ox, final double oy) {
        return closest(ox, oy, 1.0).gradientDirection;
    }

    @Override
    public double featureWidth() {
        return w;
    }

    @Override
    public double featureHeight() {
        return h;
    }

    @Override
    public boolean flipYAxis() {
        return false;
    }

    private LineSegment closest(final double ox, final double oy, final double scale) {
        final double minDist = Double.POSITIVE_INFINITY;
        final LineSegment nearest = null;
        // for (final LineSegment seg : segments) {
        // final double dist = seg.distance(new SimplePoint(oy, ox));
        // if (dist < minDist) {
        // minDist = dist;
        // nearest = seg;
        // }
        // }
        return nearest;
    }
}
