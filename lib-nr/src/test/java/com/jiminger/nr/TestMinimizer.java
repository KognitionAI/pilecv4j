package com.jiminger.nr;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestMinimizer {

    @Rule
    public ExpectedException expected = ExpectedException.none();

    static class MyMinFinc implements Minimizer.Func {

        /**
         * minimizing:
         * 
         * (x - 2)(x - 2) - 3.0;
         */
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

    @Test
    public void testMinimizerException() throws Throwable {
        expected.expect(MinimizerException.class);
        expected.expectMessage("Exception ocurred in function");

        new Minimizer(x -> {
            throw new RuntimeException("Yo");
        }).minimize(new double[] { -45.0 });
    }

    @Test
    public void testMinimizerPowellFailure() throws Throwable {
        expected.expect(MinimizerException.class);
        expected.expectMessage("iterations");

        new Minimizer(x -> {
            // whatever the current is, just return the negative. This "moving target" can't be minimized.
            return -x[0];
        }).minimize(new double[] { -45.0 });
    }
}
