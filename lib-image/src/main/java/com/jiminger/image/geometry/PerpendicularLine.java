package com.jiminger.image.geometry;

/**
 *  <p>A line defined in "perpendicular line coordinates" is expressed as a single point. This point
 * is a reference for the line that's perpendicular to the line drawn from the origin to that point.</p>  
 */
public class PerpendicularLine implements Point {
    public final Point perpRef;

    public PerpendicularLine(final Point perpRef) {
        this.perpRef = perpRef;
    }

    public PerpendicularLine(final double r, final double c) {
        perpRef = new SimplePoint(r, c);
    }

    @Override
    public double getRow() {
        return perpRef.getRow();
    }

    @Override
    public double getCol() {
        return perpRef.getCol();
    }

    @Override
    public String toString() {
        return "[" + getRow() + "," + getCol() + "]";
    }
}