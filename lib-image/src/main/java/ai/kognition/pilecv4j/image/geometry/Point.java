/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.image.geometry;

public interface Point {
    public static final double twoPi = Math.PI * 2.0;

    public static Point ocv(final org.opencv.core.Point ocvPoint) {
        return new Point() {
            @Override
            public double getRow() {
                return ocvPoint.y;
            }

            @Override
            public double getCol() {
                return ocvPoint.x;
            }

            @Override
            public String toString() {
                return Point.toString(this);
            }
        };
    }

    default public org.opencv.core.Point toOcv() {
        return new org.opencv.core.Point(x(), y());
    }

    public static String toString(final Point p) {
        return p.getClass().getSimpleName() + "[ x=" + p.x() + ", y=" + p.y() + " ]";
    }

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

    default public double distance(final Point other) {
        final Point trans = subtract(other);
        return trans.magnitude();
    }

    default public Point multiply(final double scalar) {
        return new SimplePoint(getRow() * scalar, getCol() * scalar);
    }

    default public Point crossWithZ(final boolean flipZ) {
        // !flipZ flipZ
        // | i j k | | i j k |
        // | x y 0 | | x y 0 |
        // | 0 0 1 | | 0 0 -1 |
        //
        // !flipZ = i(y * 1) - j(x * 1) => x=y*1, y=-x*1 => r=-x*1, c=y*1
        // flipZ = i(y * -1) - j(x * -1) => x=-y*1, y=x*1 => r=x*1, c=-y*1
        return flipZ ? new SimplePoint(x(), -y()) : new SimplePoint(-x(), y());
    }

    default public byte quantizedDirection() {
        final double rawang = Math.atan2(y(), x());

        // angle should be between -Pi and Pi. We want it from 0 -> 2Pi
        final double ang = rawang < 0.0 ? (2.0 * Math.PI) + rawang : rawang;
        final int bytified = (int)Math.round((ang * 256.0) / twoPi);
        return(bytified >= 256 ? 0 : (byte)(bytified & 0xff));
    }

}
