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


package com.jiminger.util;

public final class Timer
{
   private long startTime;
   private long endTime;
   
   public static final long nanoSecondsPerSecond = 1000000000L;
   public static final double secondsPerNanosecond = 1.0D/(double)nanoSecondsPerSecond;

   public final void start()
   {
      startTime = System.nanoTime();
   }

   public final String stop()
   {
      endTime = System.nanoTime();
      return toString();
   }
   
   public final float getSeconds()
   {
      return (float)((endTime - startTime) * secondsPerNanosecond);
   }

//   public final int getTenthsOfSeconds()
//   {
//      return (int)(((double)(((endTime - startTime) % 1000)) / 100) + 0.5);
//   }

   public final String toString()
   {
      return String.format("%.3f", getSeconds());
   }
}
