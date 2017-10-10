package com.jiminger.image.geometry;

public class LineSegment {
    public final Point p1;
    public final Point p2;

    private final Point p2Trans;
    private final double p2TransMagSq;
    private final boolean xbiased;

    public LineSegment(final Point p1, final Point p2) {
        this.p1 = p1;
        this.p2 = p2;

        p2Trans = p2.subtract(p1);
        p2TransMagSq = p2Trans.magnitudeSquared();
        xbiased = Math.abs(p1.x() - p2.x()) > Math.abs(p1.y() - p2.y());
    }

    public double distance(final Point x) {
        final Point xTrans = x.subtract(p1);

        // xTrans.p2Trans = |xTrans| |p2Trans| cos th
        // so the projection of x on the line from p1 to p2 is:
        // ((xTrans.p2Trans / |p2Trans|) * unit(p2Trans)) + p1 =
        // ((xTrans.p2Trans / |p2Trans|) * p2Trans /|p2Trans|) + p1 =
        // ((xTrans.p2Trans / |p2Trans|^2) * p2Trans) + p1

        final Point projection = p2Trans.multiply(xTrans.dot(p2Trans) / p2TransMagSq).add(p1);
        if (xbiased) {

        }
    }
}