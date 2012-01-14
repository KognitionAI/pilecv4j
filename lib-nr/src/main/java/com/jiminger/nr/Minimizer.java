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


package com.jiminger.nr;

import com.jiminger.util.LibraryLoader;

public class Minimizer
{
   static {
      new LibraryLoader();
   }
   
   private Func f;
   private double [] minVec;

   public static double ftol = 1.0e-10;
//   public static double TINY = 1.0e-10;
//   public static int NMAX = 5000;

   // static lock ... JNI code used is not threadsafe
   public static Object lock = new Object();

   public Minimizer(Func f)
   {
      this.f = f;
   }

   public double minimize(double [] p)
      throws MinimizerException
   {
      double [][] xi = newUnitMatrix(p.length);
      return minimize(p,xi);
   }

   public double minimize(double [] p, double [][] xi)
      throws MinimizerException
   {
      minVec = new double[p.length];
      synchronized(lock)
      {
         return dominimize(f,p,xi,ftol,minVec);
      }
   }

   public double[] getFinalPostion()
   {
      return minVec;
   }

   public interface Func
   {
      public double func(double [] x);
   }

   private static native double dominimize(Func f, double [] p, double [][] xi, 
                                           double ftol, double [] minVec)
      throws MinimizerException;

   private double [][] newUnitMatrix(int n)
   {
      double [][] ret= new double[n][];
      for (int i = 0; i < n; i++)
      {
         ret[i] = new double[n];
         for (int j = 0; j < n; j++)
            ret[i][j] = (i == j) ? 1.0 : 0.0;
      }
      return ret;
   }
}
