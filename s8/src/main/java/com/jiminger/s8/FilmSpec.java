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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.jiminger.image.Point;
import com.jiminger.image.WeightedPoint;
import com.jiminger.image.WeightedPointComparator;

public class FilmSpec {
    // general conversion
    public static final double mmPerInch = 25.4; // mm/in.
    public static final double PiOv180 = Math.PI / 180.0;
    public static final double Pi2Ov256 = (Math.PI * 2.0) / 256.0;

    // valid film types
    public static final int superEightFilmType = 1;
    public static final int eightMMFilmType = 2;

    // film spec indicies
    public static final int heightIndex = 0;
    public static final int widthIndex = 1;
    public static final int radiusIndex = 2;
    public static final int edgeToEdgeIndex = 3; // edge of film to the edge of the sprocket hole
    public static final int edgeToFrameCenterIndex = 4;
    public static final int frameHeightIndex = 5;
    public static final int frameWidthIndex = 6;
    public static final int filmWidthIndex = 7;
    public static final int frameCenterToFrameCenterIndex = 8;
    public static final int vertDistCenterSprocketToCenterFrameIndex = 9;

    // super8 spec in mm ( height, width ,radius, sprocketEdgeToFilmEdge )
    private static final double[] s8Spec = { 1.143, 0.9144, 0.07784856, 0.45, 4.2, 4.01, 5.46, 7.9, 4.23, 0.0 };

    // 8mm radius needs to be measured. It just has a place holder now.
    private static final double[] mm8Spec = { 1.23, 1.8, 0.090596, 0.91, 5.2, 3.3, 4.5, 7.9, 3.81, 3.81 / 2.0 };

    public static final int lr = 1;
    public static final int tb = 2;
    public static final int rl = 3;
    public static final int bt = 4;
    public static final String[] filmLayoutDesc = { "", "left to right", "top to bottom", "right to left", "bottom to top" };

    // These values represent possible values of the sprocketLayout
    // which is directly related to the filmLayout and whether or not
    // the film is reversed (mirrored) or not.
    public static final int alongTop = 1;
    public static final int alongRight = 2;
    public static final int alongBottom = 3;
    public static final int alongLeft = 4;
    // These lookup tables help implement the sprocketLayout method
    private static final int[] sprocketLayoutFromFilmLayoutRev = { -1, alongTop, alongRight, alongBottom, alongLeft };
    private static final int[] sprocketLayoutFromFilmLayout = { -1, alongBottom, alongLeft, alongTop, alongRight };

    public static final String[] sprocketLayoutFromFilmLayoutRevDesc = { null, "top", "right", "bottom", "left" };
    public static final String[] sprocketLayoutFromFilmLayoutDesc = { null, "bottom", "left", "top", "right" };

    /**
    * Get the sprocketLayout from the filmLayout and whether or
    *  not the film is mirrored. - It currently appears that the
    *  sprocketLayout is independent of the filmType.
    */
    public static int sprocketLayout(final int filmType, final int filmLayout, final boolean reverseImage) {
        return (reverseImage ? sprocketLayoutFromFilmLayoutRev[filmLayout] : sprocketLayoutFromFilmLayout[filmLayout]);
    }

    public static double filmAttribute(final int filmType, final int attributeIndex) {
        return filmModel(filmType)[attributeIndex];
    }

    public static double[] filmModel(final int filmType) {
        return filmType == superEightFilmType ? s8Spec : mm8Spec;
    }

    public static double inPixels(final int model, final int index, final double resulutiondpi) {
        return filmModel(model)[index] * resulutiondpi / mmPerInch;
    }

    public static boolean isVertical(final int filmLayout) {
        return ((filmLayout == tb || filmLayout == bt) ? true : false);
    }

    /**
    * This object will order a series of Transform.Fit or Transform.Cluster
    *  (but not both with the same instance) instances according to the film 
    *  layout specified
    */
    public static class SprocketHoleOrder implements Comparator<Point> {
        boolean reverseOrder;
        boolean vertical;

        public SprocketHoleOrder(final int filmLayout) {
            reverseOrder = (filmLayout == FilmSpec.bt || filmLayout == FilmSpec.rl) ? true : false;
            vertical = (filmLayout == FilmSpec.tb || filmLayout == FilmSpec.bt) ? true : false;
        }

        @Override
        public int compare(final Point o1, final Point o2) {
            if (vertical)
                return reverseOrder ? (int) (o2.getRow() - o1.getRow()) : (int) (o1.getRow() - o2.getRow());
            else
                return reverseOrder ? (int) (o2.getCol() - o1.getCol()) : (int) (o1.getCol() - o2.getCol());
        }

        @Override
        public boolean equals(final Object o) {
            return super.equals(o);
        }

    }

    /**
    * This method will filter the points using interframe geometry.
    */
    @SuppressWarnings("unchecked") // this is about the STUPIDEST needed annotation ever. Java simply SUCKS!
    // If I have a list of Points, then I have a list of WeightedPoints. Why the F@&^ doesn't java know this?
    public static <T extends WeightedPoint> boolean interframeFilter(final int filmType, final int filmLayout, final int resolutiondpi,
            final int imgLength, final List<T> points, final List<T> verifiedClusters) {
        final List<T> sprockets = new ArrayList<>();
        sprockets.addAll(points);
        Collections.sort(sprockets, new WeightedPointComparator());

        // -----------------------------------------------------------------

        // first assume the sprocket hole with the MOST hits
        // is an actual sprocket hole
        final T numOneHole = sprockets.remove(0);

        verifiedClusters.add(numOneHole);

        // now move upwards toward the far end of the image
        // 1/2 sprocket height for distance check
        final int halfSprock = ((int) (FilmSpec.inPixels(filmType, FilmSpec.heightIndex, resolutiondpi) + 0.5) + 1) / 2;

        if (gatherCorrectMatches(numOneHole, filmType, filmLayout, resolutiondpi,
                imgLength, halfSprock,
                (List<Point>) ((List<?>) sprockets), (List<Point>) ((List<?>) verifiedClusters), 1))
            return gatherCorrectMatches(numOneHole, filmType, filmLayout, resolutiondpi,
                    halfSprock, imgLength,
                    (List<Point>) ((List<?>) sprockets), (List<Point>) ((List<?>) verifiedClusters), -1);

        return false;
    }

    private static boolean gatherCorrectMatches(final Point numOneHole, final int filmType, final int filmLayout, final int resolutiondpi,
            final int imgLength, final int halfSprock, final List<Point> sprockets,
            final List<Point> verifiedClusters, final int directionMult) {
        // how many sprocket holes SHOULD I have.
        int pixBetweenSprockets = (int) (FilmSpec.inPixels(filmType, FilmSpec.frameCenterToFrameCenterIndex, resolutiondpi) + 0.5);

        if (directionMult == -1)
            pixBetweenSprockets = -pixBetweenSprockets;

        int curPos = getPointPos(numOneHole, FilmSpec.isVertical(filmLayout)) + pixBetweenSprockets;

        for (boolean done = (directionMult == 1) ? (curPos >= imgLength) : (curPos < 0); !done;) {
            // now find the nearest fit
            double mindist = -1.0;
            int nearest = -1;
            for (int i = 0; i < sprockets.size(); i++) {
                int dist = getPointPos(sprockets.get(i), FilmSpec.isVertical(filmLayout)) -
                        curPos;

                if (dist < 0)
                    dist = -dist;

                if (mindist < 0 || mindist > dist) {
                    mindist = dist;
                    nearest = i;
                }
            }

            // see if we are within 1/2 a sprocket hole width
            if (mindist > halfSprock) {
                nearest = -1;

                // if we are really close to one end then there is no problem that
                // there is a missing sprocket hole here
                // add 10 for slop
                if ((directionMult == 1 ? (curPos < (imgLength - halfSprock - 10)) : (curPos > (halfSprock + 10)))) {
                    // otherwise, we have a problem and should return that fact
                    System.out.println("No Fit near " + curPos + " ( closest is " + mindist + " away)");
                    verifiedClusters.clear();
                    return false;
                }
            }

            if (nearest >= 0) {
                final Point nc = sprockets.remove(nearest);
                verifiedClusters.add(nc);

                curPos = getPointPos(nc, FilmSpec.isVertical(filmLayout)) + pixBetweenSprockets;
            } else
                curPos = curPos + pixBetweenSprockets;

            done = (directionMult == 1) ? (curPos >= imgLength) : (curPos < 0);
        }

        return true;
    }

    public static int getPointPos(final Object fit, final boolean vertical) {
        return (int) ((vertical ? ((Point) fit).getRow() : ((Point) fit).getCol()) + 0.5);
    }

}
