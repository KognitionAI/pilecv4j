package com.jiminger.nr;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestMinimizer {

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Test
    public void testMinimizer() throws Throwable {
        final Minimizer m = new Minimizer(x -> ((x[0] - 2.0) * (x[0] - 2.0)) - 3.0);

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
