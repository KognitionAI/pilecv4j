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

package com.jiminger.image.houghspace;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opencv.core.CvType;

import com.jiminger.image.CvRaster;
import com.jiminger.image.CvRaster.FlatBytePixelSetter;
import com.jiminger.image.ImageAPI;
import com.jiminger.image.Utils;
import com.jiminger.image.geometry.Point;
import com.jiminger.image.geometry.WeightedPoint;
import com.jiminger.image.houghspace.internal.GradientDirectionMask;
import com.jiminger.image.houghspace.internal.Mask;
import com.jiminger.nr.Minimizer;
import com.jiminger.nr.MinimizerException;

public class Transform {
    private static final ImageAPI API = ImageAPI.API;

    public final double quantFactor;
    public final Mask mask;
    public final GradientDirectionMask gradDirMask;
    public final double gradientDirSlopDeg;
    public final Model model;

    public Transform(final Model model, final double quantFactor, final double scale, final double gradientDirSlopDeg) {
        this.quantFactor = quantFactor;
        this.mask = Mask.generateMask(model, quantFactor, scale);
        this.gradDirMask = GradientDirectionMask.generateGradientMask(model, model.featureWidth(), model.featureHeight(), quantFactor);
        this.gradientDirSlopDeg = gradientDirSlopDeg;
        this.model = model;
    }

    /**
     * This method assumes raster is an edge detected image. If gradient raster is supplied then it will be used to greatly improve the results.
     */
    public HoughSpace transform(final CvRaster raster, final CvRaster gradientRaster, final int houghThreshold) {
        final int height = raster.rows();
        final int width = raster.cols();
        return transform(raster, gradientRaster, houghThreshold, 0, height - 1, 0, width - 1);
    }

    public HoughSpace transform(final CvRaster raster, final CvRaster gradientRaster, final int houghThreshold,
            int rowstart, int rowend, int colstart, int colend) {
        final int height = raster.rows();
        final int width = raster.cols();

        final long gradientDirImage = gradientRaster == null ? 0 : gradientRaster.getNativeAddressOfData();

        // the size of the hough space should be quantFactor smaller
        final int htheight = (int) ((height) / quantFactor) + 1;
        final int htwidth = (int) ((width) / quantFactor) + 1;

        final short[] ret = new short[htheight * htwidth];

        final HoughSpaceEntryManager hsem = new HoughSpaceEntryManager(quantFactor);

        final ImageAPI.AddHoughSpaceEntryContributorFunc cb = (final int orow, final int ocol, final int hsr, final int hsc,
                final int hscount) -> {
            try {
                hsem.addHoughSpaceEntryContributor(orow, ocol, hsr, hsc, hscount);
            } catch (final RuntimeException rte) {
                rte.printStackTrace(System.err);
                return false;
            }
            return true;
        };

        if (rowstart < 0)
            rowstart = 0;
        if (rowend >= height)
            rowend = height - 1;
        if (colstart < 0)
            colstart = 0;
        if (colend >= width)
            colend = width - 1;

        API.Transform_houghTransformNative(raster.getNativeAddressOfData(), width, height, gradientDirImage,
                mask.mask, mask.mwidth, mask.mheight, mask.maskcr, mask.maskcc,
                gradDirMask.mask, gradDirMask.mwidth, gradDirMask.mheight, gradDirMask.maskcr, gradDirMask.maskcc,
                gradientDirSlopDeg, quantFactor, ret, htwidth, htheight, cb, houghThreshold,
                rowstart, rowend, colstart, colend, Mask.EDGE);

        hsem.entryMap.clear(); // help the gc

        return new HoughSpace(ret, htwidth, htheight, quantFactor, hsem.entries);
    }

    public List<Cluster> cluster(final List<HoughSpaceEntry> houghEntries, final double percentModelCoverage) {
        final List<Cluster> ret = new ArrayList<Cluster>();

        final double minDist = ((mask.mwidth > mask.mheight ? mask.mheight : mask.mwidth) + 1) * percentModelCoverage;

        // this is going to do rather simplistic clustering.
        for (final HoughSpaceEntry cur : houghEntries) {
            if (ret.size() == 0)
                ret.add(new Cluster(cur));
            else // see if the cur belongs within a current cluster
            {
                boolean done = false;
                for (int i = 0; i < ret.size() && !done; i++) {
                    final Cluster c = ret.get(i);
                    if (c.distance(cur) <= minDist) {
                        c.add(cur);
                        done = true;
                    }
                }
                if (!done)
                    ret.add(new Cluster(cur));
            }
        }

        return ret;
    }

    public List<Fit> bestFit(final List<Cluster> clusters, final CvRaster ti, final byte overlayPixelValueRemovedEdge,
            final byte overlayPixelValueEdge) {
        return bestFit(clusters, ti, overlayPixelValueRemovedEdge, overlayPixelValueEdge, null);
    }

    public List<Fit> bestFit(final List<Cluster> clusters, final CvRaster ti, final byte overlayPixelValueRemovedEdge,
            final byte overlayPixelValueEdge, final List<java.awt.Point> savedPruned) {
        return clusters.stream()
                .map(c -> bestFit(c, ti, overlayPixelValueRemovedEdge, overlayPixelValueEdge, savedPruned))
                .collect(Collectors.toList());
    }

    /**
     * This method will take a Cluster and use it to minimize the sum of square error 
     * with the error against the model that would fit the actual edge pixels. This is 
     * what finds the actual feature from the cluster. The passed image and overlay
     * values are for bookkeeping only. A null ti means ignore book keeping.
     */
    public Fit bestFit(final Cluster cluster, final CvRaster ti, final byte overlayPixelValueRemovedEdge, final byte overlayPixelValueEdge)
            throws MinimizerException {
        return bestFit(cluster, ti, overlayPixelValueRemovedEdge, overlayPixelValueEdge, null);
    }

    /**
     * This method will take a Cluster and use it to minimize the sum of square error with
     *  the error against the model that would fit the actual edge pixels. This is what 
     *  finds the actual feature from the cluster. The passed image and overlay values
     *  are for bookkeeping only. A null ti means ignore book keeping.
     */
    public Fit bestFit(final Cluster cluster, final CvRaster ti, final byte overlayPixelValueRemovedEdge, final byte overlayPixelValueEdge,
            final List<java.awt.Point> savedPruned)
            throws MinimizerException {
        // need to go through the raster around the cluster using the highest
        // count cluster value

        // find the original pixels that contributed to this
        // value.
        // there is a sprocket centered at e.r, e.c so we
        // need to see which pixels contribute to it
        final List<java.awt.Point> edgeVals = new ArrayList<java.awt.Point>();
        edgeVals.addAll(cluster.getContributingEdges());

        // now edgevals contains the list of all of the edge values that contributed to
        // this cluster.

        double[] result = null;

        boolean pruning = true;
        final List<java.awt.Point> pruned = new ArrayList<java.awt.Point>();
        double stdDev = -1.0;
        for (boolean done = false; !done;) {
            pruned.clear();
            final FitSumSquaresDist func = new FitSumSquaresDist(edgeVals, model);
            final Minimizer m = new Minimizer(func);
            final double[] params = new double[4];
            params[0] = cluster.imageCol();
            params[1] = cluster.imageRow();
            params[2] = 0.0;
            params[3] = 1.0;
            /* double sumSqErr = */ m.minimize(params);
            result = m.getFinalPostion();
            stdDev = func.stdDev;

            if (pruning) {
                pruning = func.prune(func.stdDev * 3.0, result, pruned);

                // This will remove one pixel at a time until the std dev
                // is below some value. It's too slow.
                // if (!pruning && func.stdDev > 1.0)
                // {
                // pruning = true;
                // func.pruneFurthest(pruned);
                // }
            }

            // if we want to write a debug image, then do it.
            final byte[] overlayRemovedEdgePixel = new byte[] { overlayPixelValueRemovedEdge };
            if (ti != null) {
                if (pruned.size() > 0) {
                    for (final java.awt.Point p : pruned)
                        ti.set(p.y, p.x, overlayRemovedEdgePixel);
                }
            }

            if (savedPruned != null)
                savedPruned.addAll(pruned);

            if (!pruning) // if we are not pruning the exit
                done = true;
        }

        if (ti != null) {
            final byte[] overlayPixelEdge = new byte[] { overlayPixelValueEdge };
            for (final java.awt.Point p : edgeVals)
                ti.set(p.y, p.x, overlayPixelEdge);
        }

        return new Fit(result[1], result[0], result[3], result[2], cluster, stdDev, edgeVals);
    }

    public static void drawClusters(final List<Cluster> clusters, final CvRaster ti, final byte color) {
        final Color colorC = new Color(color, color, color);
        for (final Cluster c : clusters)
            ti.matAp(m -> Utils.drawCircle(c.imageRow(), c.imageCol(), m, colorC));
    }

    public static void drawFits(final List<Transform.Fit> fits, final CvRaster ti, final byte color) {
        final Color colorC = new Color(color, color, color);
        for (final Fit c : fits)
            ti.matAp(m -> Utils.drawCircle((int) Math.round(c.cr), (int) Math.round(c.cc), m, colorC));
    }

    public static class HoughSpaceEntryManager {
        private final double quantFactor;
        public Map<java.awt.Point, HoughSpaceEntry> entryMap = new HashMap<java.awt.Point, HoughSpaceEntry>();
        public List<HoughSpaceEntry> entries = new ArrayList<HoughSpaceEntry>();

        HoughSpaceEntryManager(final double quantFactor) {
            this.quantFactor = quantFactor;
        }

        public void addHoughSpaceEntryContributor(final int imrow, final int imcol, final int hsr, final int hsc, final int count) {
            // find the entry from the hough space position
            final java.awt.Point hsrc = new java.awt.Point(hsc, hsr);
            HoughSpaceEntry e = entryMap.get(hsrc);
            if (e == null) {
                e = new HoughSpaceEntry(hsr, hsc, count, quantFactor);
                entryMap.put(hsrc, e);
                entries.add(e);
            }
            // System.out.println("HoughSpaceEntry:" + e);

            e.addContribution(imrow, imcol);
        }
    }

    public static class HoughSpaceEntry {
        public int r;
        public int c;
        public int count;
        public int ir;
        public int ic;
        public double quantFactor;
        public List<java.awt.Point> contributingImagePoints = new ArrayList<java.awt.Point>();

        public HoughSpaceEntry(final int r, final int c, final int count, final double quantFactor) {
            this.r = r;
            this.c = c;
            this.quantFactor = quantFactor;
            this.count = count;
            this.ir = (int) ((this.r + 1) * this.quantFactor);
            this.ic = (int) ((this.c + 1) * this.quantFactor);
        }

        public void addContribution(final int imr, final int imc) {
            contributingImagePoints.add(new java.awt.Point(imc, imr));
        }

        @Override
        public boolean equals(final Object o) {
            final HoughSpaceEntry e = (HoughSpaceEntry) o;
            return (e.r == r && e.c == c && e.count == count);
        }

        @Override
        public int hashCode() {
            return new Integer(r).hashCode() +
                    new Integer(c).hashCode() +
                    new Integer(count).hashCode();
        }

        @Override
        public String toString() {
            return "(" + r + "," + c + "," + count + ")->" + contributingImagePoints;
        }

        public static class HSEComparator implements Comparator<HoughSpaceEntry> {
            @Override
            public int compare(final HoughSpaceEntry o1, final HoughSpaceEntry o2) {
                // reverse order
                return o2.count - o1.count;
            }
        }
    }

    public static class HoughSpace {
        public HoughSpace(final short[] houghSpace, final int width, final int height,
                final double quantFactor, final List<HoughSpaceEntry> backMapEntries) {
            this.houghSpace = houghSpace;
            this.hswidth = width;
            this.hsheight = height;
            this.quantFactor = quantFactor;
            this.backMapEntries = backMapEntries;
        }

        public short[] houghSpace;
        public int hswidth;
        public int hsheight;
        public double quantFactor;
        public List<HoughSpaceEntry> backMapEntries;

        public CvRaster getTransformRaster() {
            final int width = hswidth;
            final int height = hsheight;

            final CvRaster gradRaster = CvRaster.create(height, width, CvType.CV_8UC1);

            int max = 0;
            for (int i = 0; i < houghSpace.length; i++) {
                final int count = houghSpace[i];
                if (max < count)
                    max = count;
            }

            final byte[] pixel = new byte[1];
            final double finalMax = max;
            gradRaster.apply((FlatBytePixelSetter) pos -> {
                int intVal = (int) (((houghSpace[pos]) / finalMax) * 255.0);
                if (intVal < 0)
                    intVal = 0;
                else if (intVal > 255)
                    intVal = 255;
                pixel[0] = (byte) intVal;
                return pixel;
            });

            return gradRaster;
        }

        public List<HoughSpaceEntry> getSortedEntries() {
            final List<HoughSpaceEntry> sortedSet = new LinkedList<HoughSpaceEntry>();
            sortedSet.addAll(backMapEntries);
            Collections.sort(sortedSet, new HoughSpaceEntry.HSEComparator());
            return sortedSet;
        }

        /**
         * This method does not do much any more. Now it simply writes the inverse transform (that is, 
         * the edge pixels identified by the transform) back into the image for debugging purposes.
         */
        public List<HoughSpaceEntry> inverseTransform(final CvRaster ti, final byte overlayPixelValue,
                final byte peakCircleColorValue) {
            final List<HoughSpaceEntry> sortedSet = getSortedEntries();
            final Color peakCircleColor = new Color(peakCircleColorValue, peakCircleColorValue, peakCircleColorValue);

            if (ti != null) {
                System.out.println("Constructing reverse hough transform image.");

                final byte[] overlayPixel = new byte[] { overlayPixelValue };
                for (final HoughSpaceEntry e : sortedSet) {
                    final int eir = e.ir;
                    final int eic = e.ic;

                    ti.matAp(m -> Utils.drawCircle(eir, eic, m, peakCircleColor));

                    for (final java.awt.Point p : e.contributingImagePoints)
                        ti.set(p.y, p.x, overlayPixel);
                }
            }

            return sortedSet;
        }

    }

    public static class Cluster implements WeightedPoint {
        private double ccr;
        private double ccc;
        private final List<HoughSpaceEntry> choughEntries;
        private boolean cisSorted = false;
        private double cquantFactor;
        // private int totalcount = -1;
        private List<java.awt.Point> edgeVals = null;

        public Cluster() {
            choughEntries = new ArrayList<HoughSpaceEntry>();
        }

        public Cluster(final HoughSpaceEntry e) {
            choughEntries = new ArrayList<HoughSpaceEntry>();
            add(e);
        }

        public int totalCount() {
            return getContributingEdges().size();
        }

        public int imageRow() {
            return (int) ((ccr + 1.0) * cquantFactor);
        }

        public int imageCol() {
            return (int) ((ccc + 1.0) * cquantFactor);
        }

        public double row() {
            return ccr;
        }

        public double col() {
            return ccc;
        }

        public void add(final HoughSpaceEntry e) {
            cisSorted = false;

            if (choughEntries.size() == 0) {
                ccr = (e.r);
                ccc = (e.c);
                choughEntries.add(e);
                cquantFactor = e.quantFactor;
            } else {
                final double n = (choughEntries.size());
                // find the centroid by averaging ...
                // if ccr,ccc is already an average
                // of the current houghEntries
                // then we can do an incremental
                // average.
                ccr = ((ccr * n) + (e.r)) / (n + 1.0);
                ccc = ((ccc * n) + (e.c)) / (n + 1.0);
                choughEntries.add(e);
            }
        }

        public double distance(final HoughSpaceEntry e) {
            final double dr = ccr - (e.r);
            final double dc = ccc - (e.c);
            return Math.sqrt((dr * dr) + (dc * dc));
        }

        @Override
        public String toString() {
            return "(" + imageRow() + "," + imageCol() + ")";
        }

        public int getMaxCount() {
            sortCheck();
            return choughEntries.get(0).count;
        }

        public List<HoughSpaceEntry> getHoughEntries() {
            sortCheck();
            return choughEntries;
        }

        public synchronized List<java.awt.Point> getContributingEdges() {
            if (edgeVals == null) {
                edgeVals = new ArrayList<java.awt.Point>();
                final List<HoughSpaceEntry> houghEntries = getHoughEntries();

                // we want to accumulate all of the edge vals that went
                // into this cluster
                for (int hei = 0; hei < houghEntries.size(); hei++) {
                    final HoughSpaceEntry e = houghEntries.get(hei);

                    for (final java.awt.Point p : e.contributingImagePoints) {
                        if (!edgeVals.contains(p))
                            edgeVals.add(new java.awt.Point(p.x, p.y));
                    }
                }
            }

            return Collections.unmodifiableList(edgeVals);
        }

        private void sortCheck() {
            if (!cisSorted) {
                Collections.sort(choughEntries, new HoughSpaceEntry.HSEComparator());
                cisSorted = true;
            }
        }

        // Point interface
        @Override
        public double getRow() {
            return imageRow();
        }

        @Override
        public double getCol() {
            return imageCol();
        }

        @Override
        public double getWeight() {
            return totalCount();
        }

    }

    public static class FitSumSquaresDist implements Minimizer.Func {
        private final List<java.awt.Point> edgeVals;
        private final Model sm;
        public java.awt.Point furthest;
        public double maxdist;
        public double stdDev;
        private final boolean flipYAxis;

        public FitSumSquaresDist(final List<java.awt.Point> edgeVals, final Model sm) {
            this.edgeVals = edgeVals;
            this.sm = sm;
            this.flipYAxis = sm.flipYAxis();
        }

        public boolean prune(final double maxDist, final double[] x, final List<java.awt.Point> pruned) {
            boolean ret = false;
            final double cx = x[0];
            final double cy = x[1];

            for (int i = edgeVals.size() - 1; i >= 0; i--) {
                final java.awt.Point p = edgeVals.get(i);
                final double vx = p.x - cx;
                final double vy = p.y - cy;
                final double dist = sm.distance(vx, vy, x[2], x[3]);

                if (dist >= maxDist) {
                    pruned.add(edgeVals.remove(i));
                    ret = true;
                }
            }

            return ret;
        }

        public void pruneFurthest(final List<java.awt.Point> pruned) {
            if (furthest != null) {
                boolean done = false;
                for (int i = 0; i < edgeVals.size() && !done; i++) {
                    if (furthest == edgeVals.get(i)) {
                        edgeVals.remove(i);
                        pruned.add(furthest);
                        System.out.print(".");
                        done = true;
                    }
                }
            }
        }

        @Override
        public double func(final double[] x) {
            final double cx = x[0];
            final double cy = x[1];

            maxdist = -1.0;

            double ret = 0.0;
            for (int i = 0; i < edgeVals.size(); i++) {
                final java.awt.Point p = edgeVals.get(i);
                // now, if the sprocket is centered at cx,cy -
                // we need to translate the point p into the sprocket
                // coords
                final double vx = p.x - cx;
                double vy = p.y - cy;

                if (flipYAxis)
                    vy = -vy;

                final double dist = sm.distance(vx, vy, x[2], x[3]);

                if (maxdist < dist) {
                    maxdist = dist;
                    furthest = p;
                }

                ret += (dist * dist);
            }

            stdDev = Math.sqrt(ret / edgeVals.size());

            return ret;
        }
    }

    public static class Fit implements Point {
        public final double cr; // center of sprocket instance row
        public final double cc; // center of sprocket instance col
        public final double rotation; // orientation of the sprocket instance
        public final double scale; // scale of the sprocket
        public final Cluster sourceCluster;
        public final double stdDev;
        public final List<java.awt.Point> edgeVals;
        // public int rank;

        public Fit(final double cr, final double cc, final double scale, final double rotation,
                final Cluster sourceCluster, final double stdDev, final List<java.awt.Point> edgeVals) {
            this.cr = cr;
            this.cc = cc;
            this.rotation = rotation;
            this.scale = scale;
            this.sourceCluster = sourceCluster;
            this.stdDev = stdDev;
            this.edgeVals = edgeVals;
        }

        @Override
        public String toString() {
            return "[(rc)=(" + cr + "," + cc + ") * " + scale + " ang(deg)=" + (rotation * (180.0 / Math.PI)) + "] sd=" +
                    stdDev + " " + edgeVals.size();
        }

        @Override
        public double getRow() {
            return cr;
        }

        @Override
        public double getCol() {
            return cc;
        }

        public int imageRow() {
            return (int) (cr + 0.5);
        }

        public int imageCol() {
            return (int) (cc + 0.5);
        }

        // @Override
        // public double getWeight() {
        // return rank;
        // }

        public static final Comparator<Transform.Fit> stdDeviationOrder = (o1, o2) -> o1.stdDev > o2.stdDev ? 1 : ((o1.stdDev == o2.stdDev) ? 0 : -1);

        public static final Comparator<Transform.Fit> edgeCountOrder = (o1, o2) -> o2.edgeVals.size() - o1.edgeVals.size();
    }

}
