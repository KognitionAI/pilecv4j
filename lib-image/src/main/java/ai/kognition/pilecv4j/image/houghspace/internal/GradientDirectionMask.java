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

package ai.kognition.pilecv4j.image.houghspace.internal;

import org.opencv.core.CvType;

import ai.kognition.pilecv4j.image.CvRaster;
import ai.kognition.pilecv4j.image.CvRaster.BytePixelSetter;
import ai.kognition.pilecv4j.image.houghspace.Model;

/**
 * A mask underpinned by an array of shorts that's used to hold a raster of gradient direction indications.
 */
public class GradientDirectionMask {
    public int mwidth;
    public int mheight;
    public int maskcr;
    public int maskcc;
    public byte[] mask;

    /**
     * Instantiate a mask of the given dimensions assuming that the reference point is the center of the mask.
     */
    public GradientDirectionMask(int mwidth, int mheight) {
        // mwidth and mheight need to be odd
        // so that the center falls exactly
        // on a pixel.
        mwidth += (((mwidth & 0x01) == 0) ? 1 : 0);
        mheight += (((mheight & 0x01) == 0) ? 1 : 0);

        this.mwidth = mwidth;
        this.mheight = mheight;

        this.mask = new byte[mwidth * mheight];

        this.maskcr = (this.mheight + 1) / 2 - 1;
        this.maskcc = (this.mwidth + 1) / 2 - 1;
    }

    /**
     * Generate a byte image that contains a view of the mask.
     */
    public CvRaster getMaskRaster() {
        final CvRaster raster = CvRaster.create(mheight, mwidth, CvType.CV_8UC1);
        final byte[] pixel = new byte[1];
        raster.apply((BytePixelSetter) (row, col) -> {
            final short gradDeg = get(row, col);
            int gradByte = (int) Math.round((gradDeg * 256.0) / 360.0);
            if (gradByte >= 256)
                gradByte = 0;
            pixel[0] = (byte) (gradByte & 0xff);
            return pixel;
        });
        return raster;
    }

    public static GradientDirectionMask generateGradientMask(final Model m, final double w, final double h, final double quantFactor) {
        final GradientDirectionMask gradDirMask = new GradientDirectionMask((int) ((w / quantFactor) + 1.5), (int) ((h / quantFactor) + 1.5));

        // now set the mask by sweeping the center
        final double x0 = gradDirMask.maskcc; // x0,y0 is the
        final double y0 = gradDirMask.maskcr; // origin of
                                              // the mask

        for (int r = 0; r < gradDirMask.mheight; r++) {
            for (int c = 0; c < gradDirMask.mwidth; c++) {
                // is the point r,c a possible
                // center if the center of the
                // mask is the point in question.

                // to figure this out, translate
                // r,c to the center.
                // but first, find out what r,c is
                // in the coordinate system of the
                // mask with the origin centerd.
                final double y1 = gradDirMask.mheight - r - 1 - y0;
                final double x1 = (c) - x0;

                // now, if x1,y1 is the center
                // of the sprocket hole, will
                // the origin be on the sprocket?
                // That means we need to check
                // -x1,-y1 since that is where
                // the origin will be pushed to
                // upon translating x1,y1 to the
                // origin.

                gradDirMask.set(r, c, m.gradientDirection(-x1 * quantFactor, -y1 * quantFactor));
            }
        }

        return gradDirMask;
    }

    /**
     * Set the value of the mask at a location to the given value. The value should be either EDGE or NOEDGE. Entries in the mask are accessed by row and column (not x,y).
     */
    private void set(final int r, final int c, final byte v) {
        mask[(r * mwidth) + c] = v;
    }

    /**
     * Get the value of the mask at a location The return value should be either EDGE or NOEDGE. Entries in the mask are accessed by row and column (not x,y).
     */
    private short get(final int r, final int c) {
        return mask[(r * mwidth) + c];
    }

}
