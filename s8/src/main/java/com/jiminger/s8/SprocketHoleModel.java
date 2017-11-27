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
package com.jiminger.s8;

import com.jiminger.image.houghspace.Model;

public class SprocketHoleModel implements Model {
    // the variables need to be set to the height
    // and the width of the sprocket in pixels. This
    // should be derivable from the resolution.
    private double h;
    private double w;

    // the radius of the curvature to use at the corners
    // of the sprocket hole. This should be derivable
    // from the resolution.
    private final double r;

    // hp (hprime) is h/2 - r
    // wp (wprime) is w/2 - r
    private double hp;
    private double wp;

    private final double resolutiondpmm;

    // private double centerFrameToCenterSprocketXmm;
    // private int filmType;

    private static final double PiOv180 = Math.PI / 180.0;
    // private static final double Pi2Ov256 = (Math.PI * 2.0) / 256.0;

    public SprocketHoleModel(final int resolutiondpi, final int filmType, final int filmLayout) {
        // this.filmType = filmType;
        resolutiondpmm = resolutiondpi / FilmSpec.mmPerInch;
        final double[] spec = FilmSpec.filmModel(filmType);

        final double heightmm = spec[FilmSpec.heightIndex];
        final double widthmm = spec[FilmSpec.widthIndex];
        final double radiusmm = spec[FilmSpec.radiusIndex];

        h = (heightmm * resolutiondpmm);
        w = (widthmm * resolutiondpmm);
        r = radiusmm * resolutiondpmm;
        hp = (h / 2.0) - r;
        wp = (w / 2.0) - r;

        // this ought to be enought to make the
        // mask correct sideways.
        if (!FilmSpec.isVertical(filmLayout)) {
            double tmpd = h;
            h = w;
            w = tmpd;
            tmpd = hp;
            hp = wp;
            wp = tmpd;
        }
    }

    @Override
    public double distance(final double rxx, final double ryx, final double scalex) {

        final double rx = rxx / scalex;
        final double ry = ryx / scalex;

        // to take into account the scale factor
        // we need to extend the edge of the sprocket
        // by that much. Since all of these params
        // are distances from the origin, they scale
        // directly.
        // final double swp = wp * scale;
        // final double shp = hp * scale;
        // final double sh = h * scale;
        // final double sw = w * scale;
        // final double sr = r * scale; // the radius grows by the scale factor

        final double x = Math.abs(rx);
        final double y = Math.abs(ry);

        // first determine the region
        int region;
        if (x > wp && y > hp)
            region = 2;
        else if (x > wp)
            region = 1;
        else if (y > hp)
            region = 3;
        // need to find the cross product vector of
        // | i j k |
        // | x1 x2 0 |
        // | y1 y2 0 |
        // which is 0i - 0j + (x1y2 - x2y1)k
        else if (((x * hp) - (y * wp)) > 0.0)
            region = 1;
        else
            region = 3;

        final double h2 = h / 2.0;
        final double w2 = w / 2.0;

        double dist;
        if (region == 1)
            dist = Math.abs(x - w2);
        else if (region == 3)
            dist = Math.abs(y - h2);
        else {
            final double xp = x - wp;
            final double yp = y - hp;
            dist = Math.abs(Math.sqrt((xp * xp) + (yp * yp)) - r);
        }

        return dist * scalex;
    }

    // This gradient calculation is currently unrotated.
    // I wonder if this will need to change.
    @Override
    public short gradient(final double ox, final double oy) {
        final double x = Math.abs(ox);
        final double y = Math.abs(oy);

        // first determine the region
        int region;
        if (x > wp && y > hp)
            region = 2;
        else if (x > wp)
            region = 1;
        else if (y > hp)
            region = 3;
        // need to find the cross product vector of
        // | i j k |
        // | x1 x2 0 |
        // | y1 y2 0 |
        // which is 0i - 0j + (x1y2 - x2y1)k
        else if (((x * hp) - (y * wp)) > 0.0)
            region = 1;
        else
            region = 3;

        // double h2 = h / 2.0;
        // double w2 = w / 2.0;

        // the direction should is always into the sprocket hole
        int gradDeg;
        if (region == 1) {
            // gradient is toward the origin
            // but only in the x direction
            if (ox > 0.0)
                gradDeg = 180;
            else
                gradDeg = 0;
        } else if (region == 3) {
            if (oy > 0.0)
                gradDeg = 270;
            else
                gradDeg = 90;
        } else {
            final double xp = x - wp;
            final double yp = y - hp;
            double ang;
            if (ox < 0.0 && oy > 0.0) {
                ang = Math.atan(xp / yp);
                ang += (Math.PI / 2.0);
            } else if (ox < 0.0 && oy < 0.0) {
                ang = Math.atan(yp / xp);
                ang += Math.PI;
            } else if (ox > 0.0 && oy < 0.0) {
                ang = Math.atan(xp / yp);
                ang += ((3.0 * Math.PI) / 2.0);
            } else
                ang = Math.atan(yp / xp);

            // now flip it.
            ang += Math.PI;

            // now make it go from 0 to 359 degrees
            gradDeg = (int) Math.round(ang / PiOv180);

            // mod with 360
            gradDeg = gradDeg % 360;
        }

        int gradByte = (int) Math.round((gradDeg * 256.0) / 360.0);
        if (gradByte >= 256)
            gradByte = 0;
        return (short) gradByte;
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
        return true;
    }

}
