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

package ai.kognition.pilecv4j.image.geometry.transform;

import java.util.Arrays;

import org.opencv.core.Point;

import ai.kognition.pilecv4j.image.geometry.SimplePoint;

public class ScaleRotateAndTranslate implements Transform2D {

    private final double tx;
    private final double ty;
    private final double sa;
    private final double sb;
    private final double sc;
    private final double sd;

    private static ai.kognition.pilecv4j.image.geometry.Point ocv(final Point p) {
        return ai.kognition.pilecv4j.image.geometry.Point.ocv(p);
    }

    public ScaleRotateAndTranslate(final ControlPoint p1, final ControlPoint p2) {
        final ai.kognition.pilecv4j.image.geometry.Point p1Original = ocv(p1.originalPoint);
        final ai.kognition.pilecv4j.image.geometry.Point p2Original = ocv(p2.originalPoint);
        final ai.kognition.pilecv4j.image.geometry.Point p1Transformed = ocv(p1.transformedPoint);
        final ai.kognition.pilecv4j.image.geometry.Point p2Transformed = ocv(p2.transformedPoint);

        final ai.kognition.pilecv4j.image.geometry.Point originalAtOrigin = p2Original.subtract(p1Original);
        final ai.kognition.pilecv4j.image.geometry.Point transformedAtOrigin = p2Transformed.subtract(p1Transformed);

        double angleRad = Math.atan2(transformedAtOrigin.y(), transformedAtOrigin.x()) - Math.atan2(originalAtOrigin.y(), originalAtOrigin.x());
        if(angleRad > Math.PI)
            angleRad = angleRad - (2.0 * Math.PI);
        else if(angleRad < -Math.PI)
            angleRad = angleRad + (2.0 * Math.PI);

        final double magOriginal = originalAtOrigin.magnitude();
        final double magTransformed = transformedAtOrigin.magnitude();
        final double scale = magTransformed / magOriginal;

        final double cos = Math.cos(angleRad);
        final double sin = Math.sin(angleRad);
        sa = scale * cos;
        sb = scale * (-sin);
        sc = scale * sin;
        sd = scale * cos;

        // apply the scale and rotation to original and then figure out how to translate it to get it to
        // the transformed point.
        final ai.kognition.pilecv4j.image.geometry.Point scaledAndRotOriginal = new SimplePoint(
            /* y= */ sc * p1Original.x() + sd * p1Original.y(),
            /* x= */ sa * p1Original.x() + sb * p1Original.y());

        tx = p1Transformed.x() - scaledAndRotOriginal.x();
        ty = p1Transformed.y() - scaledAndRotOriginal.y();
    }

    public ScaleRotateAndTranslate(final ControlPoints points) {
        this(sanitize(points), points.controlPoints[1]);
    }

    @Override
    public Point transform(final Point point) {
        final double x = point.x;
        final double y = point.y;

        return new Point(x * sa + y * sb + tx, x * sc + y * sd + ty);
    }

    private static ControlPoint sanitize(final ControlPoints points) {
        if(points == null)
            throw new NullPointerException("Cannot pass null controlPoints to a " + ScaleRotateAndTranslate.class.getSimpleName());
        if(points.controlPoints.length != 2)
            throw new IllegalArgumentException(
                "Can only instantiate a " + ScaleRotateAndTranslate.class.getSimpleName() + " with exactly 2 control points. You passed "
                    + points.controlPoints.length);
        if(Arrays.stream(points.controlPoints).filter(p -> p == null).findAny().isPresent())
            throw new NullPointerException(
                "Cannot pass a ControlPoints instance with any null controlPoints to a " + ScaleRotateAndTranslate.class.getSimpleName());

        return points.controlPoints[0];
    }

}
