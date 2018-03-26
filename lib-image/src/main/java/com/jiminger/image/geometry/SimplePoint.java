package com.jiminger.image.geometry;

public class SimplePoint implements Point {
    private final double r;
    private final double c;

    public SimplePoint(final double r, final double c) {
        this.r = r;
        this.c = c;
    }

    @Override
    public double getRow() {
        return r;
    }

    @Override
    public double getCol() {
        return c;
    }

    @Override
    public String toString() {
        return "(r:" + r + ",c:" + c + ")";
    }
}