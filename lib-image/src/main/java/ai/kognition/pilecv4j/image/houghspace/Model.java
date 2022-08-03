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

package ai.kognition.pilecv4j.image.houghspace;

/**
 * This interface represents a 'generator' of sorts for patterns
 * to be searched for in the image.
 */
public interface Model {
    /**
     * This method needs to be defined to return the distance from the
     * pixel position supplied, to the nearest edge of the model. This
     * method will be used to generate a mask as well as called for
     * error minimization.
     */
    default public double distance(final double ox, final double oy, final double theta, final double scale) {
        // a rotation matrix to transform point counter clockwise
        // around the origin (which is intuitively 'theta' in a
        // standard Cartesian world where the first quadrant
        // is in the upper-right hand side of the universe)
        // is given by:
        //
        // | cos(theta) -sin(theta) |
        // | |
        // | sin(theta) cos(theta) |
        //
        // Since theta means to rotate the entire model by that angle
        // (counter clockwise around the center) then, instead, we
        // can simply rotate the point around the center of the model
        // in the other direction (clockwise) before measuring the
        // distance - that is, we will simply negate theta
        final double ang = -theta;
        double rx, ry;
        if(ang != 0.0) {
            final double sinang = Math.sin(ang);
            final double cosang = Math.cos(ang);
            rx = (ox * cosang) - (oy * sinang);
            ry = (ox * sinang) + (oy * cosang);
        } else {
            rx = ox;
            ry = oy;
        }
        return distance(rx, ry, scale);
    }

    public double distance(final double rx, final double ry, final double scale);

    /**
     * This method should return the gradient direction expected at the provided
     * pixel. If the pixel isn't on an edge it should return the gradient at the
     * closest edge. The gradient should be quantized to 8-bits unsigned. In other
     * words, from 0 to 255. 0 is in the direction parallel to the positive x-axis.
     * 180 degrees should be parallel to the x-axis in the negative direction and
     * would be quantized as:
     * <code>
     *     int q = (int) Math.round((180 * 256)/360);
     *     if (q == 256) q = 0;
     *     return (byte)(q & 0xff);
     *  </code>
     */
    public byte gradientDirection(double ox, double oy);

    /**
     * This should return the extent of the model (at a scale of 1.0)
     * in pixels.
     */
    public double featureWidth();

    /**
     * This should return the extent of the model (at a scale of 1.0)
     * in pixels.
     */
    public double featureHeight();

    /**
     * The model will get edge locations passed to the distance method
     * in the coordinate system of the model (that is, translated so the
     * center of the coordinate system is the center of the model). Normally
     * the y axis will be in terms of the 'row' that the pixel is in, referenced
     * from the upper left and counting HIGHER (+ in the Y direction) as
     * the position moves DOWN the image. This is reverse of the normal
     * mathematical Cartesian space. If the model expect the normal Cartesian
     * coordinates then it should return 'true' for the following method.
     */
    public boolean flipYAxis();
}
