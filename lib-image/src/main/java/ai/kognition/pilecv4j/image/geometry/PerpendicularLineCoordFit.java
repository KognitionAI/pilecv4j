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

package ai.kognition.pilecv4j.image.geometry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ai.kognition.pilecv4j.nr.Minimizer;

/**
 * <p> This class can be used to find the best line through a set of points where the
 * result is a line in "perpendicular line coordinates." (yes, I made that term up)</p>
 * 
 * <p>A line defined in "perpendicular line coordinates" is expressed as a single point. This point
 * is a reference for the line that's perpendicular to the line drawn from the origin to that point.</p>  
 */
public class PerpendicularLineCoordFit implements Minimizer.Func {
    private final List<AwtPoint> points;

    public Point worst = null;
    public double maxErrSq;

    private final boolean weighted;
    private boolean awtp = false;

    /**
     * This constructor takes either a list of java.awt.Point's or a list of {@link AwtPoint}. If you pass it another list the Fit will fail with a RuntimeException dealing with class casting.
     * 
     * @param points
     *            is either a {@link AwtPoint} or java.awt.Points
     * @param weighted
     *            is whether or not the points are weighted.
     */
    @SuppressWarnings("unchecked")
    public PerpendicularLineCoordFit(final List<?> points, final boolean weighted) {
        this.points = new ArrayList<AwtPoint>();
        final Object o = points.get(0);
        if (o instanceof java.awt.Point) {
            for (int i = 0; i < points.size(); i++)
                this.points.add(new AwtPoint((java.awt.Point) points.get(i)));
            awtp = true;
        } else
            this.points.addAll((List<AwtPoint>) points);

        this.weighted = weighted;
    }

    public PerpendicularLineCoordFit(final List<?> points) {
        this(points, false);
    }

    public double getFurthestDistance() {
        return Math.sqrt(maxErrSq);
    }

    public double getStdDev(final double sumSqError) {
        return Math.sqrt(sumSqError / points.size());
    }

    public static PerpendicularLine interpretFinalPosition(final double[] finalPos) {
        return new PerpendicularLine(finalPos[1], finalPos[0]);
    }

    @Override
    public double func(final double[] x) {
        final double xmagsq = (x[0] * x[0]) + (x[1] * x[1]);
        final double xmag = Math.sqrt(xmagsq);

        double ret = 0.0;
        maxErrSq = -1.0;

        for (final Point p : points) {
            final double y1 = p.getRow();
            final double x1 = p.getCol();

            final double xdotxi = (y1 * x[1]) + (x1 * x[0]);
            double err = (xmag - (xdotxi / xmag));
            if (weighted)
                err *= ((WeightedPoint) p).getWeight();

            final double errSq = err * err;

            if (maxErrSq < errSq) {
                worst = p;
                maxErrSq = errSq;
            }

            ret += errSq;
        }

        return ret;
    }

    public List<?> prune(final double maxDist, final double[] x) {
        final double xmagsq = (x[0] * x[0]) + (x[1] * x[1]);
        final double xmag = Math.sqrt(xmagsq);

        final List<Object> ret = new ArrayList<Object>();

        for (final Iterator<AwtPoint> iter = points.iterator(); iter.hasNext();) {
            final Point p = iter.next();

            final double y1 = p.getRow();
            final double x1 = p.getCol();

            final double xdotxi = (y1 * x[1]) + (x1 * x[0]);
            final double err = Math.abs((xmag - (xdotxi / xmag)));

            if (err > maxDist) {
                ret.add(awtp ? (Object) (((AwtPoint) p).p) : (Object) p);
                iter.remove();
            }
        }

        return ret;
    }

    static public double perpendicularDistance(final Point x, final PerpendicularLine perpRef) {
        return perpendicularDistance(x, perpRef.x(), perpRef.y());
    }

    static private double perpendicularDistance(final Point x, final double perpRefX, final double perpRefY) {
        // We need to find the distance from a point X0 to the perp ref line described by P.
        //
        // Also define the point Xi on the line where the distance is smallest so the number
        // we are looking is | X0 - Xi |. (drawing this out helps)
        //
        // If we project X0 onto P we can see that this projected vector will be exactly
        // | X0 - Xi | longer (or shorter if X0 is on the other side of the line) than
        // the length of P itself. The length projection of X0 onto P is:
        //
        // (P.X0)/|P|
        //
        // so the distance is:
        // abs( |P| - P.X0/|P| )

        final double xmagsq = (perpRefX * perpRefX) + (perpRefY * perpRefY);
        final double xmag = Math.sqrt(xmagsq);
        final double xdotxi = (x.getRow() * perpRefY) + (x.getCol() * perpRefX);
        return Math.abs(xmag - (xdotxi / xmag));
    }

    public static double distance(final Point p1, final Point p2) {
        final double r = p1.getRow() - p2.getRow();
        final double c = p1.getCol() - p2.getCol();
        return Math.sqrt((r * r) + (c * c));
    }

    public static class AwtPoint implements Point {
        private final java.awt.Point p;

        AwtPoint(final java.awt.Point p) {
            this.p = p;
        }

        @Override
        public double getRow() {
            return p.y;
        }

        @Override
        public double getCol() {
            return p.x;
        }
    }

}
