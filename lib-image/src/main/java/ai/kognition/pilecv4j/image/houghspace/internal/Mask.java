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

package ai.kognition.pilecv4j.image.houghspace.internal;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import ai.kognition.pilecv4j.image.houghspace.Model;

/**
 * <p>
 * A mask underpinned by an array of bytes, each containing an indication as to whether or not
 * that position is the center of the model, if the center of the mask is on an EDGE in the
 * original image.
 * </p>
 *
 * <p>
 * What does that mean? If you take this mask and place it centered at an edge in the original
 * image, then everywhere that this mask reads NON-zero is potentially a "center" of the model
 * in the original image.
 * </p>
 */
public class Mask {
    public static byte EDGE = (byte)-1;
    public static byte NOEDGE = (byte)0;

    public final int mwidth;
    public final int mheight;

    /**
     * Mask center, row
     */
    public final int maskcr;

    /**
     * Mask center, column
     */
    public final int maskcc;

    /**
     * monochrome image of the mask
     */
    public final byte[] mask;

    /**
     * Instantiate a mask of the given dimensions assuming
     * that the reference point is the center of the mask.
     */
    private Mask(int mwidth, int mheight) {
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
     * Generate an OpenCV Mat image that contains a view of the mask.
     */
    public Mat getMaskImage() {
        final Mat m = new Mat(mheight, mwidth, CvType.CV_8UC1);
        m.put(0, 0, mask);
        return m;
    }

    /**
     * Set the value of the mask at a location to
     * the given value. The value should be either
     * EDGE or NOEDGE. Entries in the mask are
     * accessed by row and column (not x,y).
     */
    private void set(final int r, final int c, final byte v) {
        mask[(r * mwidth) + c] = v;
    }

    public static Mask generateMask(final Model m, final double quantFactor, final double scaleModel) {
        final double w = m.featureWidth() * scaleModel;
        final double h = m.featureHeight() * scaleModel;

        // mask is 1 pixel wider than w and higher than h
        // round(w/quant + 1) = (int)((w/quant) + 1.5)
        final Mask mask = new Mask((int)((w / quantFactor) + 1.5), (int)((h / quantFactor) + 1.5));

        // now set the mask by sweeping the center
        final double x0 = mask.maskcc; // x0,y0 is the
        final double y0 = mask.maskcr; // origin of
                                       // the mask

        for(int r = 0; r < mask.mheight; r++) {
            for(int c = 0; c < mask.mwidth; c++) {
                // is the point r,c a possible model
                // center if an edge appears at the
                // center of the mask?

                // to figure this out, translate
                // r,c to the center.
                // but first, find out what r,c is
                // in the coordinate system of the
                // mask with the origin centered.
                final double y1 = mask.mheight - r - 1 - y0;
                final double x1 = (c) - x0;

                // now, if x1,y1 is the center
                // of the sprocket hole, will
                // the origin be on the sprocket?
                // That means we need to check
                // -x1,-y1 since that is where
                // the origin will be pushed to
                // upon translating x1,y1 to the
                // origin.
                final double dist = m.distance(-(x1 * quantFactor), -(y1 * quantFactor), 0.0, 1.0);

                // if we are within a 1/2 pixel of the
                // theoretical sprocket then we're on it.
                if(dist <= quantFactor / 2.0)
                    mask.set(r, c, EDGE);
                else
                    mask.set(r, c, NOEDGE);
            }
        }

        return mask;
    }

    public static void setEdgePixVals(final byte edgePixVal, final byte noedgePixVal) {
        EDGE = edgePixVal;
        NOEDGE = noedgePixVal;
    }

}
