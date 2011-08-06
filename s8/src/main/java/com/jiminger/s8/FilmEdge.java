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
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.List;

import javax.media.jai.TiledImage;

import com.jiminger.image.PolarLineFit;
import com.jiminger.nr.Minimizer;
import com.jiminger.nr.MinimizerException;

public class FilmEdge
{
   // these contain the line in polar coordinates using
   //   the row/column (with the origin in the upper left corner)
   //   system.
   public double r;
   public double c;
   public List<Point> pixels;
   public List<Point> pruned = null;
   public double stdDev;
   public double furthestDist;

   FilmEdge(List<java.awt.Point> pixels, boolean prune)
      throws MinimizerException
   {
      this.pixels = new ArrayList<java.awt.Point>();
      this.pixels.addAll(pixels);

      findLine(prune);
   }

   /**
    * This method will cut a piece of the film edge at r,c (or the closest
    *  point along the edge line) with a total length of 'extent'.
    */
   public FilmEdge edgePiece(double x0r, double x0c, double extent, boolean usedPruned)
   {
//      // Using a polar defined line P we need to find the point on that
//      //  line closest to another point X0 (defined as Xi)
//      //
//      // P is a point px,py that defines a polar line where x cos th + y sin th = r
//      //   where th is the angle
//      //
//      // First, the origin to P
//      //   and define X0' = X0 - P
//      //       define P' = P rotated 90 deg counterclockwise = (py,-px)
//      //       define phi = th rotated 90 deg counterclockwise = th - 90
//      //     note: P' is phi degrees from the positive x-axis where
//      //           P is th degrees fro mthe positive x-axis.
//      //
//      // P'.X0' = |P'||X0'| cos phi
//      //        = |P'||X0'| (|Xi'|/|X0'|)
//      //        = |P'||Xi'|
//      //
//      //  sine Xi' is |Xi'| along the line made by P' and |Xi'| = P'.X0' / |P'| (from one 
//      //    line above). Therefore:
//      //
//      // ((P'.X0')/ |P'|) (P'/|P'|) = Xi'
//      // ((P'.X0')/|P'|^2) P' = Xi'
//      //
//      // for this function P = (c,r) so P' = (r,-c), Xi = (x0c,x0r)
//      //
//      //
//      double pmagsq = (r * r) + (c * c);
//      //
//      // note: |P'| = |P|
//      //
//      double px = c;
//      double py = r;
//
//      double ppx = py;
//      double ppy = -px;
//      double x0x = x0c;
//      double x0y = x0r;
//
//      // translate P to origin
//      double x0px = x0x - px;
//      double x0py = x0y - py;
//
//      double PpdotX0pOverPmagSq = ((ppx * x0px) + (ppy * x0py))/ pmagsq;
//      double xipx = ppx * PpdotX0pOverPmagSq;
//      double xipy = ppy * PpdotX0pOverPmagSq;
//
//      // shift from prime to normal by adding back P
//      double xix = xipx + px;
//      double xiy = xipy + py;
//
//      // now find all of edge point within extent/2 pixels from xipx, xipy


      com.jiminger.image.Point Xi = PolarLineFit.closest(new PolarLineFit.DumbPoint(x0r,x0c),c,r);
      double xix = Xi.getCol();
      double xiy = Xi.getRow();

      // now find all of edge point within extent/2 pixels from Xi
      List<Point> newPixels = new ArrayList<Point>();
      double extentOv2 = extent / 2.0;

      List<Point> pixelList = pixels;
      if (usedPruned && pruned != null && pruned.size() > 0)
      {
         pixelList = new ArrayList<Point>();
         pixelList.addAll(pixels);
         pixelList.addAll(pruned);
      }

      for (Point p : pixelList)
      {
         double dx = ((double)p.x) - xix;
         double dy = ((double)p.y) - xiy;

         double dist = Math.sqrt( dx*dx + dy*dy );

         if (dist <= extentOv2)
            newPixels.add(p);
      }
      
      FilmEdge ret;
      try
      {
         ret = newPixels.size() == 0 ? null : new FilmEdge(newPixels,true);
      }
      catch (MinimizerException me)
      {
         ret = null;
      }

      return ret;
   }

   /**
    * This will return the two edges of the film such that the first element 
    *  will be the edge that is closest to the sprocket if the image is 
    *  not reversed.
    */
   public static FilmEdge [] getEdges(
      int filmLayout, Raster edgeImage, byte edgeVal, 
      Raster gradientRaster, boolean prune)
      throws MinimizerException
   {
      List<Point> l1;
      List<Point> l2;

      if (FilmSpec.isVertical(filmLayout))
      {
         l1 = getLeftEdge(edgeImage,edgeVal,gradientRaster);
         l2 = getRightEdge(edgeImage,edgeVal,gradientRaster);
      }
      else
      {
         l1 = getTopEdge(edgeImage,edgeVal,gradientRaster);
         l2 = getBottomEdge(edgeImage,edgeVal,gradientRaster);
      }

      // if the layout is tb then the sprocketEdge is l2
      // if the layout is lr then the sprocketEdge is l2
      FilmEdge [] aret = new FilmEdge[2];
      if (filmLayout == FilmSpec.tb || filmLayout == FilmSpec.rl)
      {
         aret[0] = new FilmEdge(l2,prune);
         aret[1] = new FilmEdge(l1,prune);
      }
      else
      {
         aret[0] = new FilmEdge(l1,prune);
         aret[1] = new FilmEdge(l2,prune);
      }

      return aret;
   }

   public void writeEdge(TiledImage image, byte overlayPixelValue)
   {
      writePoints(pixels,image,overlayPixelValue);
   }

   public void writePruned(TiledImage image, byte overlayPixelValue)
   {
      if (pruned != null && pruned.size() > 0)
         writePoints(pruned,image,overlayPixelValue);
   }

   public String toString()
   {
      return "FilmEdge [" + r + "," + c + "] " + (angleWithPosXAxis() * 180.0 / Math.PI) + " deg, stddev=" + stdDev + " worst(" + 
         furthestDist + ")";
   }

   @SuppressWarnings("unchecked")
   private void findLine(boolean prune)
      throws MinimizerException
   {
      PolarLineFit func = new PolarLineFit(pixels);
      Minimizer m = new Minimizer(func);
      double sumSqError = m.minimize(startingPowell);
      double [] finalPos = m.getFinalPostion();
      stdDev = func.getStdDev(sumSqError);
      r = PolarLineFit.getRow(finalPos);
      c = PolarLineFit.getCol(finalPos);
      furthestDist = func.getFurthestDistance();

      if (prune)
      {
         pruned = new ArrayList<Point>();
         for (boolean done = false; !done;)
         {
            List<Point> locallyPruned = (List<Point>)func.prune(3.0 * stdDev, finalPos);

            if (locallyPruned.size() == 0)
               done = true;

            if (!done)
            {
               pixels.removeAll(locallyPruned);
               pruned.addAll(locallyPruned);
               sumSqError = m.minimize(finalPos);
               finalPos = m.getFinalPostion();
               stdDev = func.getStdDev(sumSqError);
               r = PolarLineFit.getRow(finalPos);
               c = PolarLineFit.getCol(finalPos);
               furthestDist = func.getFurthestDistance();
            }
         }
      }
   }

   public double radius()
   {
      return Math.sqrt((r * r) + (c * c));
   }

   public double angleWithPosXAxis()
   {
      if (c == 0.0)
         return 0.0;
      else
      {
         double ret = ((Math.PI / 2.0) - Math.atan( r / c ));

         if (ret > (Math.PI/2.0))
            ret = ret - Math.PI;

         return ret;
      }
   }

   public Point mostRight()
   {
      Point ret = null;

      int maxX = -9999;

      for (Point p : pixels)
      {
         if (maxX < p.x)
         {
            ret = p;
            maxX = p.x;
         }
      }

      return ret;
   }

   public Point mostLeft()
   {
      Point ret = null;

      int minX = 9999999;

      for (Point p : pixels)
      {
         if (minX > p.x)
         {
            ret = p;
            minX = p.x;
         }
      }

      return ret;
   }

   public Point mostBottom()
   {
      Point ret = null;

      int maxY = -1;

      for (Point p : pixels)
      {
         if (maxY < p.y)
         {
            ret = p;
            maxY = p.y;
         }
      }

      return ret;
   }

   public Point mostTop()
   {
      Point ret = null;

      int minY = 9999999;

      for (Point p : pixels)
      {
         if (minY > p.y)
         {
            ret = p;
            minY = p.y;
         }
      }

      return ret;
   }

   private static double [] startingPowell = { 512.0, 512.0 };

   private static void writePoints(List<Point> points, TiledImage image, byte overlayPixelValue)
   {
      for (Point p : points)
         image.setSample(p.x,p.y,0,(int)overlayPixelValue);
   }

   private static List<Point> getTopEdge(Raster edgeImage, byte edgeVal, 
                                  Raster gradientRaster)
   {
      double requiredGradDirDeg = 90.0; // X axis is 0 degrees
      double gradientDirSlopDeg = 90.0; // tolerance degrees
      short gradientDirSlopBytePM = // translated to short
         (short)(1.0 + (gradientDirSlopDeg * (256.0/360.0))/2.0); 
      short requiredGradDir = // looking for upward pointing gradient
         (short)(1.0 + (requiredGradDirDeg * (256.0/360.0))); 

      byte [] image = 
         ((DataBufferByte)edgeImage.getDataBuffer()).getData();
      byte [] gradient = 
         ((DataBufferByte)gradientRaster.getDataBuffer()).getData();

      int height = edgeImage.getHeight();
      int width = edgeImage.getWidth();
      List<Point> ret = new ArrayList<Point>();

      for (int c = 0; c < width; c++)
      {
         boolean done = false;
         for (int r = 0; r < height && !done; r++)
         {
            int pos = r * width + c;
            if (image[pos] == edgeVal)
            {
               short gradientDirByte = (short)intify(gradient[pos]);
               short diff = requiredGradDir > gradientDirByte ? 
                  (short)(requiredGradDir - gradientDirByte) : 
                  (short)(gradientDirByte - requiredGradDir);

               // this is the test for the
               //  gradient image
               if (gradientDirSlopBytePM >= diff || 
                   gradientDirSlopBytePM >= ((short)256 - diff))
               {
                  done = true;
                  ret.add(new Point(c,r));
               }
            }
         }
      }

      return ret;
   }

   private static List<Point> getBottomEdge(Raster edgeImage, byte edgeVal,
                                     Raster gradientRaster)
   {
      double requiredGradDirDeg = 270.0; // X axis is 0 degrees
      double gradientDirSlopDeg = 90.0; // tolerance degrees
      short gradientDirSlopBytePM = // translated to short
         (short)(1.0 + (gradientDirSlopDeg * (256.0/360.0))/2.0); 
      short requiredGradDir = // looking for upward pointing gradient
         (short)(1.0 + (requiredGradDirDeg * (256.0/360.0))); 

      byte [] image = 
         ((DataBufferByte)edgeImage.getDataBuffer()).getData();
      byte [] gradient = 
         ((DataBufferByte)gradientRaster.getDataBuffer()).getData();

      int height = edgeImage.getHeight();
      int width = edgeImage.getWidth();
      List<Point> ret = new ArrayList<Point>();

      for (int c = 0; c < width; c++)
      {
         boolean done = false;
         for (int r = height - 1; r >= 0 && !done; r--)
         {
            int pos = r * width + c;
            if (image[pos] == edgeVal)
            {
               short gradientDirByte = (short)intify(gradient[pos]);
               short diff = requiredGradDir > gradientDirByte ? 
                  (short)(requiredGradDir - gradientDirByte) : 
                  (short)(gradientDirByte - requiredGradDir);

               // this is the test for the
               //  gradient image
               if (gradientDirSlopBytePM >= diff || 
                   gradientDirSlopBytePM >= ((short)256 - diff))
               {
                  done = true;
                  ret.add(new Point(c,r));
               }
            }
         }
      }

      return ret;
   }

   private static List<Point> getLeftEdge(Raster edgeImage, byte edgeVal,
                                   Raster gradientRaster)
   {
      double requiredGradDirDeg = 0.0; // X axis is 0 degrees
      double gradientDirSlopDeg = 90.0; // tolerance degrees
      short gradientDirSlopBytePM = // translated to short
         (short)(1.0 + (gradientDirSlopDeg * (256.0/360.0))/2.0); 
      short requiredGradDir = // looking for upward pointing gradient
         (short)(1.0 + (requiredGradDirDeg * (256.0/360.0))); 

      byte [] image = 
         ((DataBufferByte)edgeImage.getDataBuffer()).getData();
      byte [] gradient = 
         ((DataBufferByte)gradientRaster.getDataBuffer()).getData();

      int height = edgeImage.getHeight();
      int width = edgeImage.getWidth();
      List<Point> ret = new ArrayList<Point>();

//      for (int i = 0; i < height; i++)
//         ret.add(new Point(1,i));

      for (int r = 0; r < height; r++)
      {
         boolean done = false;
         for (int c = 0; c < width && !done; c++)
         {
            int pos = r * width + c;
            if (image[pos] == edgeVal)
            {
               short gradientDirByte = (short)intify(gradient[pos]);
               short diff = requiredGradDir > gradientDirByte ? 
                  (short)(requiredGradDir - gradientDirByte) : 
                  (short)(gradientDirByte - requiredGradDir);

               // this is the test for the
               //  gradient image
               if (gradientDirSlopBytePM >= diff || 
                   gradientDirSlopBytePM >= ((short)256 - diff))
               {
                  done = true;
                  ret.add(new Point(c,r));
               }
            }
         }
      }

      return ret;
   }

   private static List<Point> getRightEdge(Raster edgeImage, byte edgeVal,
                                    Raster gradientRaster)
   {
      double requiredGradDirDeg = 180.0; // X axis is 0 degrees
      double gradientDirSlopDeg = 90.0; // tolerance degrees
      short gradientDirSlopBytePM = // translated to short
         (short)(1.0 + (gradientDirSlopDeg * (256.0/360.0))/2.0); 
      short requiredGradDir = // looking for upward pointing gradient
         (short)(1.0 + (requiredGradDirDeg * (256.0/360.0))); 

      byte [] image = 
         ((DataBufferByte)edgeImage.getDataBuffer()).getData();
      byte [] gradient = 
         ((DataBufferByte)gradientRaster.getDataBuffer()).getData();

      int height = edgeImage.getHeight();
      int width = edgeImage.getWidth();
      List<Point> ret = new ArrayList<Point>();

//      for (int i = 0; i < height; i++)
//         ret.add(new Point(width-1,i));

      for (int r = 0; r < height; r++)
      {
         boolean done = false;
         for (int c = width - 1; c >= 0 && !done; c--)
         {
            int pos = r * width + c;
            if (image[pos] == edgeVal)
            {
               short gradientDirByte = (short)intify(gradient[pos]);
               short diff = requiredGradDir > gradientDirByte ? 
                  (short)(requiredGradDir - gradientDirByte) : 
                  (short)(gradientDirByte - requiredGradDir);

               // this is the test for the
               //  gradient image
               if (gradientDirSlopBytePM >= diff || 
                   gradientDirSlopBytePM >= ((short)256 - diff))
               {
                  done = true;
                  ret.add(new Point(c,r));
               }
            }
         }
      }

      return ret;
   }

   private static int intify(byte b)
   {
      return (b < 0) ? ((int)b) + 256 : (int)b;
   }
}
