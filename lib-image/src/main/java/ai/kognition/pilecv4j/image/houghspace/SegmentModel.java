package ai.kognition.pilecv4j.image.houghspace;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import ai.kognition.pilecv4j.image.geometry.LineSegment;
import ai.kognition.pilecv4j.image.geometry.Point;
import ai.kognition.pilecv4j.image.geometry.SimplePoint;

public class SegmentModel implements Model {

    final private LineSegment[] segments;
    // final private double num;
    // final private double minX;
    // final private double minY;
    // final private double maxX;
    // final private double maxY;
    final private double w;
    final private double h;

    final private double shiftrow;
    final private double shiftcol;

    public SegmentModel(final Collection<LineSegment> segments) {
        if (segments == null || segments.size() == 0)
            throw new IllegalArgumentException();

        final LineSegment[] array = segments.stream().toArray(LineSegment[]::new);
        this.segments = array;
        // this.num = this.segments.length;

        final List<Point> points = segments.stream()
                .map(l -> Arrays.asList(l.p1, l.p2))
                .flatMap(ps -> ps.stream())
                .collect(Collectors.toList());

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (final Point p : points) {
            final double x = p.x();
            if (x < minX)
                minX = x;
            if (x > maxX)
                maxX = x;
            final double y = p.y();
            if (y < minY)
                minY = y;
            if (y > maxY)
                maxY = y;
        }

        this.w = maxX - minX;
        this.h = maxY - minY;

        // move minX to -1/2 w
        final double halfw = this.w / 2.0;
        this.shiftcol = 0.0 - (minX - halfw);

        final double halfh = this.h / 2.0;
        this.shiftrow = 0.0 - (minY - halfh);
    }

    @Override
    public double distance(final double ox, final double oy, final double scale) {
        final double x = shiftcol + ox;
        final double y = shiftrow + oy;

        double minDist = Double.POSITIVE_INFINITY;
        for (final LineSegment seg : segments) {
            final double dist = seg.distance(new SimplePoint(y, x));
            if (dist < minDist)
                minDist = dist;
        }
        return minDist;
    }

    @Override
    public byte gradientDirection(final double ox, final double oy) {
        final double x = shiftcol + ox;
        final double y = shiftrow + oy;
        return closest(x, y, 1.0).gradientDirection;
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
        double minDist = Double.POSITIVE_INFINITY;
        LineSegment nearest = null;
        for (final LineSegment seg : segments) {
            final double dist = seg.distance(new SimplePoint(oy, ox));
            if (dist < minDist) {
                minDist = dist;
                nearest = seg;
            }
        }
        return nearest;
    }
}
