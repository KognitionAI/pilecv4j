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

package com.jiminger.image;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jiminger.nr.Minimizer;

/**
 * This class can be used to find the best line through 
 *  a set of points where the result is a line in polar
 *  coordinates.
 */

public class PolarLineFit implements Minimizer.Func {
   private final List<AwtPoint> points;
   
   public Point worst = null;
   public double maxErrSq;
   
   private final boolean weighted;
   private boolean awtp = false;

   /**
    * This constructor takes either a list of java.awt.Point's or a list of
    * {@link AwtPoint}. If you pass it another list the Fit will fail with a 
    * RuntimeException dealing with class casting.
    * 
    * @param points is either a {@link AwtPoint} or java.awt.Points
    * @param weighted is whether or not the points are weighted.
    */
   @SuppressWarnings("unchecked")
   public PolarLineFit(List<?> points, boolean weighted){
      this.points = new ArrayList<AwtPoint>();
      Object o = points.get(0);
      if (o instanceof java.awt.Point)
      {
         for (int i = 0; i < points.size(); i++)
            this.points.add(new AwtPoint((java.awt.Point)points.get(i)));
         awtp = true;
      }
      else
         this.points.addAll((List<AwtPoint>)points);

      this.weighted = weighted;
   }

   public PolarLineFit(List<?> points){
      this(points,false);
   }

   public double getFurthestDistance() {
      return Math.sqrt(maxErrSq);
   }

   public double getStdDev(double sumSqError) {
      return Math.sqrt(sumSqError / (double)points.size());
   }

   public static double getRow(double [] finalPos) {
      // finalPos is [x,y]
      return finalPos[1];
   }

   public static double getCol(double [] finalPos) {
      // finalPos is [x,y]
      return finalPos[0];
   }

   public static double getx(double [] finalPos) {
      // finalPos is [x,y]
      return finalPos[0];
   }

   public static double gety(double [] finalPos) {
      // finalPos is [x,y]
      return finalPos[1];
   }
   
   public double func(double [] x) {
      double xmagsq = (x[0] * x[0]) + (x[1] * x[1]);
      double xmag = Math.sqrt(xmagsq);

      double ret = 0.0;
      maxErrSq = -1.0;

      for (Point p : points)
      {
         double y1 = p.getRow();
         double x1 = p.getCol();

         double xdotxi = (y1 * x[1]) + (x1 * x[0]);
         double err = (xmag - (xdotxi / xmag));
         if (weighted)
            err *= ((WeightedPoint)p).getWeight();

         double errSq = err * err;

         if (maxErrSq < errSq)
         {
            worst = p;
            maxErrSq = errSq;
         }

         ret += errSq;
      }

      return ret;
   }

   public List<?> prune(double maxDist, double [] x)
   {
      double xmagsq = (x[0] * x[0]) + (x[1] * x[1]);
      double xmag = Math.sqrt(xmagsq);

      List<Object> ret = new ArrayList<Object>();

      for (Iterator<AwtPoint> iter = points.iterator(); iter.hasNext();)
      {
         Point p = iter.next();
         
         double y1 = p.getRow();
         double x1 = p.getCol();

         double xdotxi = (y1 * x[1]) + (x1 * x[0]);
         double err = Math.abs((xmag - (xdotxi / xmag)));

         if (err > maxDist)
         {
            ret.add(awtp ? (Object)(((AwtPoint)p).p) : (Object)p);
            iter.remove();
         }
      }

      return ret;
   }

   static public double perpendicularDistance(Point x, double polarx, double polary)
   {
      // We need to find the distance from a point X0 to the polar line described by P.
      //   also define the point Xi on the line where the distance is smallest so the number
      //   we are looking is | X0 - Xi |. (drawing this out helps)
      //
      // If we project X0 onto P we can see that this projected vector will be exactly 
      //  | X0 - Xi | longer (or shorter if X0 is on the other side of the line) than
      //  the length of P itself. The length projection of X0 onto P is:
      //
      //  (P.X0)/|P|
      //
      // so the distance is:
      //  abs( |P| - P.X0/|P| )

      double xmagsq = (polarx * polarx) + (polary * polary);
      double xmag = Math.sqrt(xmagsq);
      double xdotxi = (x.getRow() * polary) + (x.getCol() * polarx);
      return Math.abs(xmag - (xdotxi / xmag));
   }

   public static double distance(Point p1, Point p2)
   {
      double r = p1.getRow() - p2.getRow();
      double c = p1.getCol() - p2.getCol();
      return Math.sqrt((r*r)+(c*c));
   }

   public static class AwtPoint implements Point {
      private java.awt.Point p;

      AwtPoint(java.awt.Point p) {
         this.p = p;
      }

      public double getRow() { return (double)p.y; }
      public double getCol() { return (double)p.x; }
   }

}
