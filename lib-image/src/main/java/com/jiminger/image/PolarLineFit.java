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

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.media.jai.TiledImage;

import com.jiminger.nr.Minimizer;

/**
 * This class can be used to find the best line through 
 *  a set of points where the result is a line in polar
 *  coordinates.
 */

public class PolarLineFit implements Minimizer.Func
{
   private List<PPoint> points;
   public Point worst = null;
   public double maxErrSq;
   private boolean weighted;
   private boolean awtp = false;

   /**
    * This constructor takes either a list of java.awt.Point's or a list of
    * PPoints. If you pass it another list the Fit will fail with a 
    * RuntimeException dealing with class casting.
    * 
    * @param points is either a PPoints or java.awt.Points
    * @param weighted is whether or not the points are weighted.
    */
   @SuppressWarnings("unchecked")
   public PolarLineFit(List<?> points, boolean weighted)
   {
      this.points = new ArrayList<PPoint>();
      Object o = points.get(0);
      if (o instanceof java.awt.Point)
      {
         for (int i = 0; i < points.size(); i++)
            this.points.add(new PPoint((java.awt.Point)points.get(i)));
         awtp = true;
      }
      else
         this.points.addAll((List<PPoint>)points);

      this.weighted = weighted;
   }

   public PolarLineFit(List<?> points)
   {
      this(points,false);
   }

   public double getFurthestDistance()
   {
      return Math.sqrt(maxErrSq);
   }

   public double getStdDev(double sumSqError)
   {
      return Math.sqrt(sumSqError / (double)points.size());
   }

   public static double getRow(double [] finalPos)
   {
      // finalPos is [x,y]
      return finalPos[1];
   }

   public static double getCol(double [] finalPos)
   {
      // finalPos is [x,y]
      return finalPos[0];
   }

   public static double getx(double [] finalPos)
   {
      // finalPos is [x,y]
      return finalPos[0];
   }

   public static double gety(double [] finalPos)
   {
      // finalPos is [x,y]
      return finalPos[1];
   }

   public double func(double [] x)
   {
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

      for (Iterator<PPoint> iter = points.iterator(); iter.hasNext();)
      {
         Point p = iter.next();
         
         double y1 = p.getRow();
         double x1 = p.getCol();

         double xdotxi = (y1 * x[1]) + (x1 * x[0]);
         double err = Math.abs((xmag - (xdotxi / xmag)));

         if (err > maxDist)
         {
            ret.add(awtp ? (Object)(((PPoint)p).p) : (Object)p);
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

   static public Point closest(Point x, double polarx, double polary)
   {
      // Here we use the description for the perpendicularDistance.
      //  if we translate X0 to the origin then Xi' (defined as
      //  Xi translated by X0) will be at |P| - (P.X0)/|P| (which 
      //  is the signed magnitude of the X0 - Xi where the sign will
      //  be positive if X0 X polar(P) is positive and negative 
      //  otherwise (that is, if X0 is on the "lower" side of the polar
      //  line described by P)) along P itself. So:
      //
      // Xi' = (|P| - (P.X0)/|P|) Pu = (|P| - (P.X0)/|P|) P/|P|
      //     = (1 - (P.X0)/|P|^2) P (where Pu is the unit vector in the P direction)
      //
      // then we can translate it back by X0 so that gives:
      //
      // Xi = (1 - (P.X0)/|P|^2) P + X0 = c P + X0
      //  where c = (1 - (P.X0)/|P|^2)
      double Pmagsq = (polarx * polarx) + (polary * polary);
      double PdotX0 = (x.getRow() * polary) + (x.getCol() * polarx);

      double c = (1.0 - (PdotX0 / Pmagsq));
      return new DumbPoint( (c * polary) + x.getRow(), (c * polarx) + x.getCol() );
   }

   public static double distance(Point p1, Point p2)
   {
      double r = p1.getRow() - p2.getRow();
      double c = p1.getCol() - p2.getCol();
      return Math.sqrt((r*r)+(c*c));
   }

   // some randome thoughts which went nowhere.....
      //  P def by [ px, py ]. Imagine translating this polar line so the point
      //  P is at the origin. We can define P' as a vector P along this new line with 
      //  the same magnitude as P. If you think about it, this is simply the same thing
      //  as rotating P by 90 degres counterclockwise. So P' is [ py, -px ].
      // 
      //  to clarify: P' is parallel to the polar line described by P and is perpendicular
      //   to P itself. Now that we have defined P' we are going to translate the 
      //   other points so that P (not P') is at the origin. So we define:
      //
      //  X0' = X0 - P
      //  Xi' = Xi - P (Xi is the point along the polar line described by P where
      //    X0 comes the closest - this intuitively means that Xi' is the point
      //    along P' itself where X0' comes the closest to P'.
      //
      //  (drawing all this out helps)
      //
      //  now. P'.X0' is |P'||X0'| cos (th - 90) =>
      //  P'.X0' = |P||X0'| cos phi (|P| = |P'| and phi defined as th - 90 deg)
      //   but cos phi is Adjacent (|Xi'|) over hypotenuse (|X0'|) so
      //  P'.X0' = |P||X0'| (|Xi'|/|X0'|) = |P||Xi'| =>
      //  |Xi'| = P'.X0' / |P|  (1.0)
      //
      // Xi' itself is |Xi'| distance along P' which is = |Xi'|P'/|P|
      //   so, from this and (1.0) Xi' = P' ((P'.X0') / (|P| ^ 2))
      //
      // now. the distance from X0 to Xi' is the same as the distance 
      //  from X0' to Xi' and this distance is:
      //
      //  |X0' - Xi'| = 

   public static class PPoint implements Point
   {
      private java.awt.Point p;

      PPoint(java.awt.Point p)
      {
         this.p = p;
      }

      public double getRow() { return (double)p.y; }
      public double getCol() { return (double)p.x; }
   }

   public static class DumbPoint implements Point
   {
      private double r;
      private double c;

      public DumbPoint(double r, double c)
      {
         this.r = r;
         this.c = c;
      }

      public double getRow() { return r; }
      public double getCol() { return c; }
   }

   static public void drawPolarLine(double r, double c, TiledImage ti, Color color)
   {
      drawPolarLine(r,c,ti,color,0,0,ti.getHeight()-1,ti.getWidth()-1);
   }

   static public void drawPolarLine(double r, double c, TiledImage ti, Color color, 
                             int boundingr1, int boundingc1, int boundingr2, int boundingc2)
   {
      drawPolarLine(r, c, ti, color, boundingr1, boundingc1, boundingr2, boundingc2, 0, 0);
   }

   static public void drawPolarLine(double r, double c, TiledImage ti, Color color, 
                             int boundingr1, int boundingc1, int boundingr2, int boundingc2,
                             int translater, int translatec)
   {
      int tmpd;
      if (boundingr1 > boundingr2)
      { tmpd = boundingr1; boundingr1 = boundingr2; boundingr2 = tmpd; }

      if (boundingc1 > boundingc2)
      { tmpd = boundingc1; boundingc1 = boundingc2; boundingc2 = tmpd; }

      Graphics2D g = ti.createGraphics();
      g.setColor(color);

      // a polar line represented by r,c is a perpendicular to
      //  the line from the origin to the point r,c. The line
      //  from the origin to this point in rad,theta is given
      //  by:
      //
      //     rad = sqrt(r^2 + c^2)
      //     theta = tan^-1(r/c)
      //        (where theta is measured from the top of the 
      //         image DOWN to the point r,c)
      //
      //  anyway - the line is represented by:
      //   x cos(theta) + y sin (theta) = r

      double rad = Math.sqrt((r * r) + (c * c));

      // we need to find the endpoints of the line:
      int r1, c1, r2, c2;

      // lets remove the simple possiblities
      if (c == 0.0)
      {
         r1 = r2 = (int)(rad + 0.5);
         c1 = boundingc1;
         c2 = boundingc2;
      }
      else if (r == 0.0)
      {
         c1 = c2 = (int)(rad + 0.5);
         r1 = boundingr1;
         r2 = boundingr2;
      }
      else
      {
         double sintheta = r / rad;
         double costheta = c / rad;

         // x cos th + y sin th = r =>
         // x (xc/r) + y (yc/r) = r (by definition of sin and cos) =>
         // x xc + y yc = r^2 =>
         // X.Xc = r^2 - (no duh!)

         // find the points at the boundaries

         // where does the line intersect the left/right boundary
         //  bc costh + ir sinth = r =>
         //
         //        r - bc costh
         //  ir = -------------
         //           sinth
         //
         double leftIntersetingRow = (rad - (((double)boundingc1) * costheta)) / sintheta;
         double rightIntersetingRow = (rad - (((double)boundingc2) * costheta)) / sintheta;

         // where does the line intersect the top/bottom boundary
         //  ic costh + br sinth = r =>
         //
         //        r - br sinth
         //  ic = -------------
         //           costh
         //
         double topIntersectingCol = (rad - (((double)boundingr1) * sintheta)) / costheta;
         double botIntersectingCol = (rad - (((double)boundingr2) * sintheta)) / costheta;

         // now, which pair works the best
         c1 = r1 = -1;
         if (leftIntersetingRow >= (double)boundingr1 && leftIntersetingRow <= (double)boundingr2)
         { c1 = boundingc1; r1 = (int)(leftIntersetingRow + 0.5); }
         else if (topIntersectingCol >= (double)boundingc1 && topIntersectingCol <= (double)boundingc2)
         { c1 = boundingr1; r1 = (int)(topIntersectingCol + 0.5); }
         else if (rightIntersetingRow >= (double)boundingr1 && rightIntersetingRow <= (double)boundingr2)
         { c1 = boundingc2; r1 = (int)(rightIntersetingRow + 0.5); }
         else if (botIntersectingCol >= (double)boundingc1 && botIntersectingCol <= (double)boundingc2)
         { c1 = boundingr2; r1 = (int)(botIntersectingCol + 0.5); }

         if (c1 == -1 && r1 == -1) // no part of the line intersects the box
//         {
//            System.out.println( " line " + r + "," + c + " does not intesect " +
//                                boundingr1 + "," + boundingc1 + "," + boundingr2 + "," + boundingc2);
            return;
//         }

         // now search in the reverse direction for the other point
         c2 = r2 = -1;
         if (botIntersectingCol >= (double)boundingc1 && botIntersectingCol <= (double)boundingc2)
         { c2 = boundingr2; r2 = (int)(botIntersectingCol + 0.5); }
         else if (rightIntersetingRow >= (double)boundingr1 && rightIntersetingRow <= (double)boundingr2)
         { c2 = boundingc2; r2 = (int)(rightIntersetingRow + 0.5); }
         else if (topIntersectingCol >= (double)boundingc1 && topIntersectingCol <= (double)boundingc2)
         { c2 = boundingr1; r2 = (int)(topIntersectingCol + 0.5); }
         else if (leftIntersetingRow >= (double)boundingr1 && leftIntersetingRow <= (double)boundingr2)
         { c2 = boundingc1; r2 = (int)(leftIntersetingRow + 0.5); }

         // now, the two points should not be the same ... but anyway
      }

      g.drawLine(c1 + translatec,r1 + translater,c2 + translatec,r2 + translater);
   }

   public static void drawBoundedPolarLine(Point bound1, Point bound2, double r, double c,
                                           TiledImage ti, Color color)
   {
      drawLine(closest(bound1, c, r), closest(bound2, c, r), ti, color);
   }

   public static void drawCircle(Point p, TiledImage ti, Color color)
   {
      Graphics2D g = ti.createGraphics();
      g.setColor(color);

      int radius = 10;
      g.drawOval(((int)(p.getCol() + 0.5))-radius,
                 ((int)(p.getRow() + 0.5))-radius,
                 2 * radius,2 * radius);
   }

   public static void drawLine(Point p1, Point p2, TiledImage ti, Color color)
   {
      Graphics2D g = ti.createGraphics();
      g.setColor(color);

      g.drawLine((int)(p1.getCol() + 0.5),(int)(p1.getRow() + 0.5),(int)(p2.getCol() + 0.5),(int)(p2.getRow() + 0.5));
   }

}
