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

import com.jiminger.houghspace.*;

public class SprocketHoleModel implements Model
{
   // the variables need to be set to the height
   //  and the width of the sprocket in pixels. This
   //  should be derivable from the resolution.
   private double h;
   private double w;

   // the radius of the curvature to use at the corners
   //  of the sprocket hole. This should be derivable 
   //  from the resolution.
   private double r;

   // hp (hprime) is h/2 - r
   // wp (wprime) is w/2 - r
   private double hp;
   private double wp;

   private double resolutiondpmm;

//   private double centerFrameToCenterSprocketXmm;
//   private int filmType;

   private static final double PiOv180 = Math.PI / 180.0;
//   private static final double Pi2Ov256 = (Math.PI * 2.0) / 256.0;

   public SprocketHoleModel(int resolutiondpi, int filmType, int filmLayout)
   {
//      this.filmType = filmType;
      resolutiondpmm = (double)resolutiondpi / FilmSpec.mmPerInch;
      double [] spec = FilmSpec.filmModel(filmType);

      double heightmm = spec[FilmSpec.heightIndex];
      double widthmm = spec[FilmSpec.widthIndex];
      double radiusmm = spec[FilmSpec.radiusIndex];

      h = (heightmm * resolutiondpmm);
      w = (widthmm * resolutiondpmm);
      r = radiusmm * resolutiondpmm;
      hp = (h / 2.0) - r;
      wp = (w / 2.0) - r;

      // this ought to be enought to make the 
      //  mask correct sideways.
      if (! FilmSpec.isVertical(filmLayout))
      {
         double tmpd = h;
         h = w;
         w = tmpd;
         tmpd = hp;
         hp = wp;
         wp = tmpd;
      }
   }

   public double distance(double ox, double oy, double theta, double scale)
   {
      // a rotation matrix to transform point counter clockwise
      //  around the origin (which is intuitively 'theta' in a 
      //  standard Cartesian world where the first quadrant 
      //  is in the upper-right hand side of the universe)
      //  is given by:
      // 
      //  | cos(theta)  -sin(theta) |
      //  |                         |
      //  | sin(theta)   cos(theta) |
      //
      // Since theta means to rotate the entire model by that angle
      //  (counter clockwise around the center) then, instead, we
      //  can simply rotate the point around the center of the sprocket
      //  in the other direction (clockwise) before measuring the 
      //  distance - that is, we will simply negate theta
      double ang = -theta;
      double rx,ry;
      if (ang != 0.0)
      {
         double sinang = Math.sin(ang);
         double cosang = Math.cos(ang);
         rx = (ox * cosang) - (oy * sinang);
         ry = (ox * sinang) + (oy * cosang);
      }
      else
      {
         rx = ox;
         ry = oy;
      }

      // to take into account the scale factor
      //  we need to extend the edge of the sprocket
      //  by that much. Since all of these params
      //  are distances from the origin, they scale 
      //  directly.
      double swp = wp * scale;
      double shp = hp * scale;
      double sh = h * scale;
      double sw = w * scale;
      double sr = r * scale; // the radius grows by the scale factor

      double x = Math.abs(rx);
      double y = Math.abs(ry);

      // first determine the region
      int region;
      if (x > swp && y > shp)
         region = 2;
      else if (x > swp)
         region = 1;
      else if (y > shp)
         region = 3;
      // need to find the cross product vector of
      // | i    j    k |
      // | x1   x2   0 |
      // | y1   y2   0 |
      // which is 0i - 0j + (x1y2 - x2y1)k
      else if (((x * shp) - (y * swp)) > 0.0)
         region = 1;
      else
         region = 3;

      double sh2 = sh / 2.0;
      double sw2 = sw / 2.0;

      double dist;
      if (region == 1)
         dist = Math.abs(x - sw2);
      else if (region == 3)
         dist = Math.abs(y - sh2);
      else
      {
         double xp = x - swp;
         double yp = y - shp;
         dist = Math.abs(Math.sqrt((xp * xp) + (yp * yp)) - sr);
      }

      return dist;
   }

   // This gradient calculation is currently unrotated.
   //  I wonder if this will need to change.
   public short gradient(double ox, double oy)
   {
      double x = Math.abs(ox);
      double y = Math.abs(oy);

      // first determine the region
      int region;
      if (x > wp && y > hp)
         region = 2;
      else if (x > wp)
         region = 1;
      else if (y > hp)
         region = 3;
      // need to find the cross product vector of
      // | i    j    k |
      // | x1   x2   0 |
      // | y1   y2   0 |
      // which is 0i - 0j + (x1y2 - x2y1)k
      else if (((x * hp) - (y * wp)) > 0.0)
         region = 1;
      else
         region = 3;

//      double h2 = h / 2.0;
//      double w2 = w / 2.0;

      // the direction should is always into the sprocket hole
      int gradDeg;
      if (region == 1)
      {
         // gradient is toward the origin 
         //  but only in the x direction
         if (ox > 0.0)
            gradDeg = 180;
         else
            gradDeg = 0;
      }
      else if (region == 3)
      {
         if (oy > 0.0)
            gradDeg = 270;
         else
            gradDeg = 90;
      }
      else
      {
         double xp = x - wp;
         double yp = y - hp;
         double ang;
         if (ox < 0.0 && oy > 0.0)
         {
            ang = Math.atan(xp/yp);
            ang += (Math.PI/2.0);
         }
         else if (ox < 0.0 && oy < 0.0)
         {
            ang = Math.atan(yp/xp);
            ang += Math.PI;
         }
         else if (ox > 0.0 && oy < 0.0)
         {
            ang = Math.atan(xp/yp);
            ang += ((3.0 * Math.PI)/2.0);
         }
         else
            ang = Math.atan(yp/xp);

         // now flip it.
         ang += Math.PI;

         // now make it go from 0 to 359 degrees
         gradDeg = (int)Math.round(ang / PiOv180);

         // mod with 360
         gradDeg = gradDeg % 360;
      }

      int gradByte = (int)Math.round(((double)gradDeg * 256.0)/360.0);
      if (gradByte >= 256)
         gradByte = 0;
      return (short)gradByte;
   }

   public double featureWidth()
   {
      return w;
   }

   public double featureHeight()
   {
      return h;
   }

   public boolean flipYAxis()
   {
      return true;
   }

}

