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


import com.jiminger.image.CvRaster;

public class Correlate
{
   public static double [] correlation(CvRaster X, CvRaster Y)
      throws CorrelateException {
      if (X.rows != Y.rows || X.cols != Y.cols)
         throw new CorrelateException("images don't have the same dimmentions");
      
      if (X.channels != Y.channels)
          throw new CorrelateException("images don't have the same number of bands");

      int width = X.cols;
      int height = X.rows;
      int wh = width * height;

      int dim = X.channels;
      double [] ret = new double [dim];

      // find the means
      double Xbar;
      double Ybar;

      byte [] bandx = (byte[])X.data;
      byte [] bandy = (byte[])Y.data;

      for (int b = 0; b < dim; b++) {
         Xbar = 0.0;
         Ybar = 0.0;

         int pos = b;
         for(int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
               Xbar += (double)(bandx[pos] & 0xff);
               Ybar += (double)(bandy[pos] & 0xff);
               pos += dim;
            }
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

         pos = b;
         for(int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
               XmXbar = (double)(bandx[pos] & 0xff) - Xbar;
               YmYbar = (double)(bandy[pos] & 0xff) - Ybar;

               // calculate the mag sq
               varx += (XmXbar * XmXbar);
               vary += (YmYbar * YmYbar);
               
               pos += dim;
            }
         }

         double denom = Math.sqrt(varx * vary);
         double numerator = 0.0;
         
         pos = b;
         for(int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
               XmXbar = (double)(bandx[pos] & 0xff) - Xbar;
               YmYbar = (double)(bandy[pos] & 0xff) - Ybar;

               numerator += XmXbar * YmYbar;
               pos += dim;
            }
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

