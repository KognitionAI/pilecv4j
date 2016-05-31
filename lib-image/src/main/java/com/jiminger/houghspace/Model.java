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


package com.jiminger.houghspace;

/**
 * This interface represents a 'generator' of sorts for patterns
 *  to be searched for in the image.
 */
public interface Model
{
   /**
    * This method needs to be defined to return the distance from the
    *  pixel position supplied, to the nearest edge of the model. This
    *  method will be used to generate a mask as well as called for
    *  error minimization.
    */
   public double distance(double ox, double oy, double theta, double scale);

   /**
    * This method should return the gradient direction expected at the provided
    *  pixel if it is on an edge that makes up the object being searched for.
    *  Currently the result should be in degrees. quantized to a one degree
    *  level.
    */
   public short gradient(double ox, double oy);

   /**
    * This should return the extent of the model (at a scale of 1.0)
    *  in pixels.
    */
   public double featureWidth();

   /**
    * This should return the extent of the model (at a scale of 1.0)
    *  in pixels.
    */
   public double featureHeight();

   /**
    * The model will get edge locations passed to the distance method
    *  in the coordinate system of the model (that is, translated so the
    *  center of the coordinate system is the center of the model). Normally
    *  the y axis will be in terms of the 'row' that the pixel is in, referenced
    *  from the upper left and counting HIGHER (+ in the Y direction) as
    *  the position moves DOWN the image. This is reverse of the normal
    *  mathematical Cartesian space. If the model expect the normal Cartesian
    *  coordinates then it should return 'true' for the following method.
    */
   public boolean flipYAxis();
}

