package com.jiminger.nr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestMinimizer {

    static class MyMinFinc implements Minimizer.Func {

        @Override
        public double func(final double[] x0) {
            final double x = x0[0] - 2.0;
            return (x * x) - 3.0;
        }

    }

    @Test
    public void testMinimizer() throws Throwable {
        final Minimizer.Func f = new MyMinFinc();

        final Minimizer m = new Minimizer(f);

        final double minVal = m.minimize(new double[] { -45.0 });
        final double minParam = m.getFinalPostion()[0];

        assertEquals(-3.0, minVal, 0.0001);
        assertEquals(2.0, minParam, 0.0001);
    }
}
