package com.jiminger.nr.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.jiminger.nr.LinearRegression;
import com.jiminger.nr.Minimizer;
import com.jiminger.util.LibraryLoader;

public class TestLinearRegression
{
   static
   {
      new LibraryLoader();
   }
   
   @Test
   public void testLinearRegression() throws Throwable
   {
      double[] x = new double[11];
      double[] y = new double[11];
      
      for (int i = 0; i < 11; i++)
      {
         x[i] = (double)(i - 5);
         y[i] = x[i] + ((((0x01 & i) == 0) ? 1 : -1) * 0.0345);
      }
      // because of the odd number of pertebations, this causes the regression to be
      // off from what is intuitive. We fix this be removing the center pertebation
      y[5] = x[5];
      
      LinearRegression lr = new LinearRegression(x,y);
      
      Minimizer minimizer = new Minimizer(lr);
      double[] m = new double[2];
      m[0] = 0.0;
      m[1] = 1.0;
      double err = minimizer.minimize(m);
      double[] newm = minimizer.getFinalPostion();
      
      assertEquals(1.0D, newm[0], 0.0001);
      assertEquals(0.0D,newm[1], 0.01);
      assertEquals((0.0345D * 0.0345D) * 10, err, 0.001);
   }
}
