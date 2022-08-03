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

import java.util.Arrays;

import ai.kognition.pilecv4j.nr.Minimizer.Func;

/**
 * <p>
 * NOTE: DO NOT USE THIS CLASS. IT'S ONLY KEPT FOR REFERENCE.
 * The error term is calculated using the perpendicular distance. This is LESS robust
 * for determining the slope/intercept form of a line than minimizing the vertical
 * distance (i.e. the error in the 'y' term only). because the starting point cannot
 * be rotated through the Y axis. As an example, if the slope of the points is approximately
 * -3.0 and the slope of the starting iteration is 3.0, then RAISING the slope
 * lowers the perpendicular distance. This moves the iterations in the wrong direction.
 * </p>
 *
 * This class will do a linear regression by minimizing the squared error
 * between the points provided to the constructor and the line specified
 * by y = m[0]x + m[1]
 */
@Deprecated
public class LinearRegression implements Func {

    private final double[] y;
    private final double[] x;

    public LinearRegression(final double[] x, final double[] y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public double func(final double[] lineDefMb) {
        final double m = lineDefMb[0];
        final double b = lineDefMb[1];

        System.out.println(Arrays.toString(lineDefMb));

        // translate the line so it goes through the origin and find a unit vector
        final double t = -b;
        final double yTransWhenXis1 = m;
        final double tmpMag = Math.sqrt((yTransWhenXis1 * yTransWhenXis1) + 1.0);
        final double xut = 1.0 / tmpMag;
        final double yut = yTransWhenXis1 / tmpMag;
        // [ xut, yut ] = a unit vector in the direction of the line y = mx.
        // This is the line y = mx + b translated so it goes through the origin.

        // now we want to translate each point the same amount (i.e., by 't')
        // and measure the perpendicular distance to the unit vector [ xut, yut ]

        double error2 = 0.0;
        for(int i = 0; i < x.length; i++) {
            final double yit = y[i] + t;
            final double xi = x[i];

            // dot product [ xi, yi ] with [ xut, yut ] = the length of
            // the projection of [ xi, yi ] onto [ xut, yut ]

            final double dot = (xi * xut) + (yit * yut);

            final double projXi = xut * dot;
            final double projYit = yut * dot;

            // the error is the distance between [ projXi, projYit ] and [ xi, yit ]
            final double diffX = projXi - xi;
            final double diffY = projYit - yit;
            final double curErr2 = (diffX * diffX) + (diffY * diffY);

            System.out.print("" + curErr2 + " ");

            // sum the squared error.
            error2 += curErr2;
        }

        System.out.println(" = " + error2);

        return error2;
    }

}
