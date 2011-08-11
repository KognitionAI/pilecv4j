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


/**
 *  The correlation coef for a deterministic (or sample) signal is:
 *
 *                      Sum-over-i( (Xi - Xbar)(Yi - Ybar) )
 * r(x,y) =  --------------------------------------------------------------
 *           sqrt( Sum-over-i( (Xi - Xbar)^ 2) * Sum-over-i( (Yi - Ybar)^ 2)
 *
 * where:
 *   r(X,Y) is the cross-correlation coef between image X and image Y
 *   Xi is the ith pixel value in the RV (image) X 
 *   Yi is the ith pixel value in the RV (image) Y
 *   Xbar is the expected value for X 
 *   Ybar is the expected value for Y 
 */

import java.awt.image.*;
import javax.media.jai.*;

@SuppressWarnings("restriction")
public class Correlate
{
   public static double [] correlation(RenderedImage X, RenderedImage Y)
      throws CorrelateException
   {
      if (X.getHeight() != Y.getHeight() ||
          X.getWidth() != Y.getWidth())
         throw new CorrelateException("images don't have the same dimmentions");

      int width = X.getWidth();
      int height = X.getHeight();
      int wh = width * height;

      RenderedImage [] srcs = new RenderedImage[2];
      srcs[0] = X;
      srcs[1] = Y;
      RasterFormatTag[] tags = RasterAccessor.findCompatibleTags(srcs,X);
      RasterFormatTag xtag = tags[0];
      RasterFormatTag ytag = tags[1];

      Raster xraster = X.getData();
      RasterAccessor xra = 
         new RasterAccessor(xraster, xraster.getBounds(), xtag, X.getColorModel());

      Raster yraster = Y.getData();
      RasterAccessor yra = 
         new RasterAccessor(yraster, yraster.getBounds(), ytag, Y.getColorModel());

      byte bandsx[][] = xra.getByteDataArrays();
      int xBandOffsets[] = xra.getBandOffsets();
      int xPixelStride = xra.getPixelStride();
      int xScanLineStride = xra.getScanlineStride();

      byte bandsy[][] = yra.getByteDataArrays();
      int yBandOffsets[] = yra.getBandOffsets();
      int yPixelStride = yra.getPixelStride();
      int yScanLineStride = yra.getScanlineStride();

      if (xBandOffsets.length != yBandOffsets.length)
         throw new CorrelateException("images don't have the same number of bands");

      int dim = xBandOffsets.length;
      double [] ret = new double [dim];

      int xBegCurRow;
      int yBegCurRow;
      int xpos;
      int ypos;

      // find the means
      double Xbar;
      double Ybar;

      byte [] bandx;
      byte [] bandy;

      for (int b = 0; b < dim; b++)
      {
         bandx = bandsx[b];
         bandy = bandsy[b];
         xBegCurRow = xBandOffsets[b];
         yBegCurRow = yBandOffsets[b];

         Xbar = 0.0;
         Ybar = 0.0;

         for(int row = 0; row < height; row++)
         {
            xpos = xBegCurRow;
            ypos = yBegCurRow;

            for (int col = 0; col < width; col++)
            {
               Xbar += (double)(bandx[xpos] & 0xff);
               Ybar += (double)(bandy[ypos] & 0xff);

               xpos += xPixelStride;
               ypos += yPixelStride;
            }

            xBegCurRow += xScanLineStride;
            yBegCurRow += yScanLineStride;
         }

         Xbar /= (double)wh;
         Ybar /= (double)wh;

         // now Xbar and Ybar are set appropriately.

         // since this is a normalized correlation we will
         //  calculate the denominator now
         double XmXbar;
         double YmYbar;
         double varx = 0.0;
         double vary = 0.0;

         xBegCurRow = xBandOffsets[b];
         yBegCurRow = yBandOffsets[b];
         for(int row = 0; row < height; row++)
         {
            xpos = xBegCurRow;
            ypos = yBegCurRow;

            for (int col = 0; col < width; col++)
            {
               XmXbar = (double)(bandx[xpos] & 0xff) - Xbar;
               YmYbar = (double)(bandy[ypos] & 0xff) - Ybar;

               // calculate the mag sq
               varx += (XmXbar * XmXbar);
               vary += (YmYbar * YmYbar);

               xpos += xPixelStride;
               ypos += yPixelStride;
            }

            xBegCurRow += xScanLineStride;
            yBegCurRow += yScanLineStride;
         }

         double denom = Math.sqrt(varx * vary);

         double numerator = 0.0;
         xBegCurRow = xBandOffsets[b];
         yBegCurRow = yBandOffsets[b];
         for(int row = 0; row < height; row++)
         {
            xpos = xBegCurRow;
            ypos = yBegCurRow;

            for (int col = 0; col < width; col++)
            {
               XmXbar = (double)(bandx[xpos] & 0xff) - Xbar;
               YmYbar = (double)(bandy[ypos] & 0xff) - Ybar;

               xpos += xPixelStride;
               ypos += yPixelStride;

               numerator += XmXbar * YmYbar;
            }

            xBegCurRow += xScanLineStride;
            yBegCurRow += yScanLineStride;
         }

         ret[b] = numerator/denom;
      }

      return ret;
   }

   public static class CorrelateException extends Exception
   {
      /**
     * 
     */
    private static final long serialVersionUID = -2120461761070300410L;

    public CorrelateException(String message)
      {
         super(message);
      }
   }

   public static String printDoubleArray(double [] ar)
   {
      String ret = "[";
      for (int i = 0; i < ar.length - 1; i++)
         ret += Double.toString(ar[i]) + ", ";
      ret += Double.toString(ar[ar.length - 1]) + "]";

      return ret;
   }
}

