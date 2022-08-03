/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.nr;

import ai.kognition.pilecv4j.nr.Minimizer.Func;

/**
 * <p>
 * This class will do a linear regression by minimizing the squared error
 * between the points provided to the constructor and the line specified
 * by y = m[0]x + m[1]
 * </p>
 *
 * <p>
 * NOTE: The error term is calculated using the vertical distance. That is
 * it minimizes the error in the 'y' term only. This is more robust for determining
 * the slope/intercept form of a line because the starting point cannot be rotated
 * through the Y axis. In other words, if the slope of the points is approximately
 * -3.0 and the slope of the starting iteration is 3.0, then RAISING the slope
 * lowers the perpendicular distance. This moves the iterations in the wrong
 * direction.
 * </p>
 */
public class SimpleLinearRegression implements Func {

    public final double[] y;
    public final double[] x;

    public SimpleLinearRegression(final double[] x, final double[] y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public double func(final double[] lineDefMb) {
        final double m = lineDefMb[0];
        final double b = lineDefMb[1];
        double error2 = 0.0;
        for(int i = 0; i < x.length; i++) {
            final double ycur = (m * x[i] + b);
            final double ecur = ycur - y[i];
            error2 += (ecur * ecur);
        }

        return error2;
    }

}
