package com.jiminger.nr;

import com.jiminger.nr.Minimizer.Func;

/**
 * This class will do a linear regression by minimizing the squared error
 * between the points provided to the constructor and the line specified
 * by y = m[0]x + m[1]
 */
public class LinearRegression implements Func
{

   private double[] y;
   private double[] x;
   
   public LinearRegression(double[] x, double[] y)
   {
      this.x = x;
      this.y = y;
   }
   
   @Override
   public double func(double[] m)
   {
      // TODO: this minimizes the difference in the 'y'. I might want to minimize the
      // perpendicular distance to the line.
      double error2 = 0.0;
      for (int i = 0; i < x.length; i++)
      {
         double ycur = (m[0] * x[i] + m[1]);
         double ecur = ycur - y[i];
         error2 += (ecur * ecur);
      }
      
      return error2;
   }

}
