package com.jiminger.nr.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.jiminger.nr.Minimizer;
import com.jiminger.util.LibraryLoader;

public class TestMinimizer
{
	static LibraryLoader ll = new LibraryLoader();
	
	static class MyMinFinc implements Minimizer.Func
	{

		public double func(double[] x0) 
		{
			double x = x0[0] - 2.0;
			return (x * x) - 3.0;
		}
		
	}
	
	@Test
	public void testMinimizer() throws Throwable
	{
		Minimizer.Func f = new MyMinFinc();
		
		Minimizer m = new Minimizer(f);
		
		double minVal = m.minimize(new double[] { -45.0 });
		double minParam = m.getFinalPostion()[0];
		
		assertEquals(-3.0,minVal,0.0001);
		assertEquals(2.0,minParam,0.0001);
	}

}
