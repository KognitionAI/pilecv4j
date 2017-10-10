/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

package com.jiminger.image.geometry;

public interface Point {
    public double getRow();

    public double getCol();

    default public double x() {
        return getCol();
    }

    default public double y() {
        return getRow();
    }

    /**
     * This will return a point that's translated such that if the point passed in
     * is the same as {@code this} then the result will be the [0, 0].
     * 
     * It basically results in [ this - toOrigin ];
     */
    default public Point subtract(final Point toOrigin) {
        return new SimplePoint(y() - toOrigin.y(), x() - toOrigin.x());
    }

    default public Point add(final Point toOrigin) {
        return new SimplePoint(y() + toOrigin.y(), x() + toOrigin.x());
    }

    default public double magnitudeSquared() {
        final double y = y();
        final double x = x();
        return (y * y) + (x * x);
    }

    default public double magnitude() {
        return Math.sqrt(magnitudeSquared());
    }

    default public double dot(final Point other) {
        return (x() * other.x()) + (y() * other.y());
    }

    default public Point multiply(final double scalar) {
        return new SimplePoint(getRow() * scalar, getCol() * scalar);
    }
}
