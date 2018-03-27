package com.jiminger.nr;

import com.jiminger.nr.Minimizer.Func;

/**
 * This class will do a linear regression by minimizing the squared error
 * between the points provided to the constructor and the line specified
 * by y = m[0]x + m[1]
 */
public class LinearRegression implements Func {

    private final double[] y;
    private final double[] x;

    public LinearRegression(final double[] x, final double[] y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public double func(final double[] lineDefMb) {
        // TODO: this minimizes the difference in the 'y'. I might want to minimize the
        // perpendicular distance to the line.
        final double m = lineDefMb[0];
        final double b = lineDefMb[1];
        double error2 = 0.0;
        for (int i = 0; i < x.length; i++) {
            final double ycur = (m * x[i] + b);
            final double ecur = ycur - y[i];
            error2 += (ecur * ecur);
        }

        return error2;
    }

}
