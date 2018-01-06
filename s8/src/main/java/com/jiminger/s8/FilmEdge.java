package com.jiminger.s8;
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

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import com.jiminger.image.CvRaster;
import com.jiminger.image.Utils;
import com.jiminger.image.geometry.PerpendicularLine;
import com.jiminger.image.geometry.PerpendicularLineCoordFit;
import com.jiminger.image.geometry.SimplePoint;
import com.jiminger.nr.Minimizer;
import com.jiminger.nr.MinimizerException;

public class FilmEdge {
    // these contain the line in perpendicular line coordinates using
    // the row/column (with the origin in the upper left corner)
    // system.
    public PerpendicularLine edge;
    public List<Point> pixels;
    public List<Point> pruned = null;
    public double stdDev;
    public double furthestDist;

    FilmEdge(final List<java.awt.Point> pixels, final boolean prune)
            throws MinimizerException {
        this.pixels = new ArrayList<java.awt.Point>();
        this.pixels.addAll(pixels);

        findLine(prune);
    }

    /**
     * This method will cut a piece of the film edge at r,c (or the closest point along the edge line) with a total length of 'extent'.
     */
    public FilmEdge edgePiece(final double x0r, final double x0c, final double extent, final boolean usedPruned) {
        final com.jiminger.image.geometry.Point Xi = Utils.closest(new SimplePoint(x0r, x0c), edge);
        final double xix = Xi.getCol();
        final double xiy = Xi.getRow();

        // now find all of edge point within extent/2 pixels from Xi
        final List<Point> newPixels = new ArrayList<Point>();
        final double extentOv2 = extent / 2.0;

        List<Point> pixelList = pixels;
        if (usedPruned && pruned != null && pruned.size() > 0) {
            pixelList = new ArrayList<Point>();
            pixelList.addAll(pixels);
            pixelList.addAll(pruned);
        }

        for (final Point p : pixelList) {
            final double dx = (p.x) - xix;
            final double dy = (p.y) - xiy;

            final double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist <= extentOv2)
                newPixels.add(p);
        }

        FilmEdge ret;
        try {
            ret = newPixels.size() == 0 ? null : new FilmEdge(newPixels, true);
        } catch (final MinimizerException me) {
            ret = null;
        }

        return ret;
    }

    /**
     * This will return the two edges of the film such that the first element will be the edge that is closest to the sprocket if the image is not reversed.
     */
    public static FilmEdge[] getEdges(final int filmLayout, final CvRaster edgeImage, final byte edgeVal, final CvRaster gradientRaster,
            final boolean prune)
            throws MinimizerException {
        List<Point> l1;
        List<Point> l2;

        if (FilmSpec.isVertical(filmLayout)) {
            l1 = getLeftEdge(edgeImage, edgeVal, gradientRaster);
            l2 = getRightEdge(edgeImage, edgeVal, gradientRaster);
        } else {
            l1 = getTopEdge(edgeImage, edgeVal, gradientRaster);
            l2 = getBottomEdge(edgeImage, edgeVal, gradientRaster);
        }

        // if the layout is tb then the sprocketEdge is l2
        // if the layout is lr then the sprocketEdge is l2
        final FilmEdge[] aret = new FilmEdge[2];
        if (filmLayout == FilmSpec.tb || filmLayout == FilmSpec.rl) {
            aret[0] = new FilmEdge(l2, prune);
            aret[1] = new FilmEdge(l1, prune);
        } else {
            aret[0] = new FilmEdge(l1, prune);
            aret[1] = new FilmEdge(l2, prune);
        }

        return aret;
    }

    public void writeEdge(final CvRaster image, final byte overlayPixelValue) {
        writePoints(pixels, image, overlayPixelValue);
    }

    public void writePruned(final CvRaster image, final byte overlayPixelValue) {
        if (pruned != null && pruned.size() > 0)
            writePoints(pruned, image, overlayPixelValue);
    }

    @Override
    public String toString() {
        return "FilmEdge " + edge + " " + (angleWithPosXAxis() * 180.0 / Math.PI) + " deg, stddev=" + stdDev + " worst(" +
                furthestDist + ")";
    }

    private void findLine(final boolean prune)
            throws MinimizerException {
        final PerpendicularLineCoordFit func = new PerpendicularLineCoordFit(pixels);
        final Minimizer m = new Minimizer(func);
        double sumSqError = m.minimize(startingPowell);
        double[] finalPos = m.getFinalPostion();
        stdDev = func.getStdDev(sumSqError);
        edge = PerpendicularLineCoordFit.interpretFinalPosition(finalPos);
        furthestDist = func.getFurthestDistance();

        if (prune) {
            pruned = new ArrayList<Point>();
            for (boolean done = false; !done;) {
                @SuppressWarnings("unchecked")
                final List<Point> locallyPruned = (List<Point>) func.prune(3.0 * stdDev, finalPos);

                if (locallyPruned.size() == 0)
                    done = true;

                if (!done) {
                    pixels.removeAll(locallyPruned);
                    pruned.addAll(locallyPruned);
                    sumSqError = m.minimize(finalPos);
                    finalPos = m.getFinalPostion();
                    stdDev = func.getStdDev(sumSqError);
                    edge = PerpendicularLineCoordFit.interpretFinalPosition(finalPos);
                    furthestDist = func.getFurthestDistance();
                }
            }
        }
    }

    public double angleWithPosXAxis() {
        final double c = edge.getCol();
        if (c == 0.0)
            return 0.0;
        else {
            double ret = ((Math.PI / 2.0) - Math.atan(edge.getRow() / c));

            if (ret > (Math.PI / 2.0))
                ret = ret - Math.PI;

            return ret;
        }
    }

    public Point mostRight() {
        Point ret = null;

        int maxX = -9999;

        for (final Point p : pixels) {
            if (maxX < p.x) {
                ret = p;
                maxX = p.x;
            }
        }

        return ret;
    }

    public Point mostLeft() {
        Point ret = null;

        int minX = 9999999;

        for (final Point p : pixels) {
            if (minX > p.x) {
                ret = p;
                minX = p.x;
            }
        }

        return ret;
    }

    public Point mostBottom() {
        Point ret = null;

        int maxY = -1;

        for (final Point p : pixels) {
            if (maxY < p.y) {
                ret = p;
                maxY = p.y;
            }
        }

        return ret;
    }

    public Point mostTop() {
        Point ret = null;

        int minY = 9999999;

        for (final Point p : pixels) {
            if (minY > p.y) {
                ret = p;
                minY = p.y;
            }
        }

        return ret;
    }

    private static double[] startingPowell = { 512.0, 512.0 };

    private static void writePoints(final List<Point> points, final CvRaster image, final byte overlayPixelValue) {
        final byte[] overlayPixel = new byte[] { overlayPixelValue };
        for (final Point p : points)
            image.set(p.y, p.x, overlayPixel);
    }

    private static List<Point> getTopEdge(final CvRaster edgeImage, final byte edgeVal, final CvRaster gradientRaster) {
        final double requiredGradDirDeg = 90.0; // X axis is 0 degrees
        final double gradientDirSlopDeg = 90.0; // tolerance degrees
        final short gradientDirSlopBytePM = // translated to short
                (short) (1.0 + (gradientDirSlopDeg * (256.0 / 360.0)) / 2.0);
        final short requiredGradDir = // looking for upward pointing gradient
                (short) (1.0 + (requiredGradDirDeg * (256.0 / 360.0)));

        final byte[] image = (byte[]) CvRaster.copyToPrimitiveArray(edgeImage);
        final byte[] gradient = (byte[]) CvRaster.copyToPrimitiveArray(gradientRaster);

        final int height = edgeImage.rows;
        final int width = edgeImage.cols;
        final List<Point> ret = new ArrayList<Point>();

        for (int c = 0; c < width; c++) {
            boolean done = false;
            for (int r = 0; r < height && !done; r++) {
                final int pos = r * width + c;
                if (image[pos] == edgeVal) {
                    final short gradientDirByte = (short) intify(gradient[pos]);
                    final short diff = requiredGradDir > gradientDirByte ? (short) (requiredGradDir - gradientDirByte)
                            : (short) (gradientDirByte - requiredGradDir);

                    // this is the test for the
                    // gradient image
                    if (gradientDirSlopBytePM >= diff ||
                            gradientDirSlopBytePM >= ((short) 256 - diff)) {
                        done = true;
                        ret.add(new Point(c, r));
                    }
                }
            }
        }

        return ret;
    }

    private static List<Point> getBottomEdge(final CvRaster edgeImage, final byte edgeVal, final CvRaster gradientRaster) {
        final double requiredGradDirDeg = 270.0; // X axis is 0 degrees
        final double gradientDirSlopDeg = 90.0; // tolerance degrees
        final short gradientDirSlopBytePM = // translated to short
                (short) (1.0 + (gradientDirSlopDeg * (256.0 / 360.0)) / 2.0);
        final short requiredGradDir = // looking for upward pointing gradient
                (short) (1.0 + (requiredGradDirDeg * (256.0 / 360.0)));

        final byte[] image = (byte[]) CvRaster.copyToPrimitiveArray(edgeImage);
        final byte[] gradient = (byte[]) CvRaster.copyToPrimitiveArray(gradientRaster);

        final int height = edgeImage.rows;
        final int width = edgeImage.cols;
        final List<Point> ret = new ArrayList<Point>();

        for (int c = 0; c < width; c++) {
            boolean done = false;
            for (int r = height - 1; r >= 0 && !done; r--) {
                final int pos = r * width + c;
                if (image[pos] == edgeVal) {
                    final short gradientDirByte = (short) intify(gradient[pos]);
                    final short diff = requiredGradDir > gradientDirByte ? (short) (requiredGradDir - gradientDirByte)
                            : (short) (gradientDirByte - requiredGradDir);

                    // this is the test for the
                    // gradient image
                    if (gradientDirSlopBytePM >= diff ||
                            gradientDirSlopBytePM >= ((short) 256 - diff)) {
                        done = true;
                        ret.add(new Point(c, r));
                    }
                }
            }
        }

        return ret;
    }

    private static List<Point> getLeftEdge(final CvRaster edgeImage, final byte edgeVal, final CvRaster gradientRaster) {
        final double requiredGradDirDeg = 0.0; // X axis is 0 degrees
        final double gradientDirSlopDeg = 90.0; // tolerance degrees
        final short gradientDirSlopBytePM = // translated to short
                (short) (1.0 + (gradientDirSlopDeg * (256.0 / 360.0)) / 2.0);
        final short requiredGradDir = // looking for upward pointing gradient
                (short) (1.0 + (requiredGradDirDeg * (256.0 / 360.0)));

        final byte[] image = (byte[]) CvRaster.copyToPrimitiveArray(edgeImage);
        final byte[] gradient = (byte[]) CvRaster.copyToPrimitiveArray(gradientRaster);

        final int height = edgeImage.rows;
        final int width = edgeImage.cols;
        final List<Point> ret = new ArrayList<Point>();

        // for (int i = 0; i < height; i++)
        // ret.add(new Point(1,i));

        for (int r = 0; r < height; r++) {
            boolean done = false;
            for (int c = 0; c < width && !done; c++) {
                final int pos = r * width + c;
                if (image[pos] == edgeVal) {
                    final short gradientDirByte = (short) intify(gradient[pos]);
                    final short diff = requiredGradDir > gradientDirByte ? (short) (requiredGradDir - gradientDirByte)
                            : (short) (gradientDirByte - requiredGradDir);

                    // this is the test for the
                    // gradient image
                    if (gradientDirSlopBytePM >= diff ||
                            gradientDirSlopBytePM >= ((short) 256 - diff)) {
                        done = true;
                        ret.add(new Point(c, r));
                    }
                }
            }
        }

        return ret;
    }

    private static List<Point> getRightEdge(final CvRaster edgeImage, final byte edgeVal, final CvRaster gradientRaster) {
        final double requiredGradDirDeg = 180.0; // X axis is 0 degrees
        final double gradientDirSlopDeg = 90.0; // tolerance degrees
        final short gradientDirSlopBytePM = // translated to short
                (short) (1.0 + (gradientDirSlopDeg * (256.0 / 360.0)) / 2.0);
        final short requiredGradDir = // looking for upward pointing gradient
                (short) (1.0 + (requiredGradDirDeg * (256.0 / 360.0)));

        final byte[] image = (byte[]) CvRaster.copyToPrimitiveArray(edgeImage);
        final byte[] gradient = (byte[]) CvRaster.copyToPrimitiveArray(gradientRaster);

        final int height = edgeImage.rows;
        final int width = edgeImage.cols;
        final List<Point> ret = new ArrayList<Point>();

        // for (int i = 0; i < height; i++)
        // ret.add(new Point(width-1,i));

        for (int r = 0; r < height; r++) {
            boolean done = false;
            for (int c = width - 1; c >= 0 && !done; c--) {
                final int pos = r * width + c;
                if (image[pos] == edgeVal) {
                    final short gradientDirByte = (short) intify(gradient[pos]);
                    final short diff = requiredGradDir > gradientDirByte ? (short) (requiredGradDir - gradientDirByte)
                            : (short) (gradientDirByte - requiredGradDir);

                    // this is the test for the
                    // gradient image
                    if (gradientDirSlopBytePM >= diff ||
                            gradientDirSlopBytePM >= ((short) 256 - diff)) {
                        done = true;
                        ret.add(new Point(c, r));
                    }
                }
            }
        }

        return ret;
    }

    private static int intify(final byte b) {
        return (b < 0) ? (b) + 256 : (int) b;
    }
}
