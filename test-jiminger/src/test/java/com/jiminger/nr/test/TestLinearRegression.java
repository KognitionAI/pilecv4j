package com.jiminger.nr.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.jiminger.nr.LinearRegression;
import com.jiminger.nr.LinearRegressionWithKnownSlope;
import com.jiminger.nr.Minimizer;

public class TestLinearRegression {
    public static double[] slopes = { -3.0D, -2.0D, -1.0D, 0.0D, 1.0D, 2.0D, 3.0D };
    public static double[] yintercepts = { -3.0D, -2.0D, -1.0D, 0.0D, 1.0D, 2.0D, 3.0D };

    @Test
    public void testLinearRegression() throws Throwable {
        for (final double slope : slopes) {
            for (final double yintercept : yintercepts)
                runRegression(yintercept, slope);
        }
    }

    public void runRegression(final double yintercept, final double slope) throws Throwable {
        final double[] x = new double[11];
        final double[] y = new double[11];

        for (int i = 0; i < 11; i++) {
            x[i] = i - 5;
            y[i] = (x[i] * slope) + ((((0x01 & i) == 0) ? 1 : -1) * 0.0345) + yintercept;
        }
        // because of the odd number of pertebations, this causes the regression to be
        // off from what is intuitive. We fix this be removing the center pertebation
        y[5] = x[5] + yintercept;

        final LinearRegression lr = new LinearRegression(x, y);

        Minimizer minimizer = new Minimizer(lr);
        double[] m = new double[2];
        m[0] = -slope;
        m[1] = -yintercept;
        double err = minimizer.minimize(m);
        double[] newm = minimizer.getFinalPostion();

        assertEquals(slope, newm[0], 0.0001);
        assertEquals(yintercept, newm[1], 0.01);
        assertEquals((0.0345D * 0.0345D) * 10, err, 0.001);

        final LinearRegressionWithKnownSlope lrws = new LinearRegressionWithKnownSlope(slope, x, y);
        minimizer = new Minimizer(lrws);
        m = new double[1];
        m[0] = -yintercept;
        err = minimizer.minimize(m);
        newm = minimizer.getFinalPostion();

        assertEquals(yintercept, newm[0], 0.01);
        assertEquals((0.0345D * 0.0345D) * 10, err, 0.001);
    }
}
