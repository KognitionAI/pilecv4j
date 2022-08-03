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

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.Utils;

public class AffineTransform implements Transform2D {

    private final double tx;
    private final double ty;
    private final double sa;
    private final double sb;
    private final double sc;
    private final double sd;

    public AffineTransform(final ControlPoints cps) {
        final Point[] src = new Point[cps.controlPoints.length];
        final Point[] dst = new Point[cps.controlPoints.length];

        int index = 0;
        for(final ControlPoint cp: cps.controlPoints) {
            src[index] = cp.originalPoint;
            dst[index++] = cp.transformedPoint;
        }

        final double[][] transform;
        try(CvMat cvmat = CvMat.move(Imgproc.getAffineTransform(new MatOfPoint2f(src), new MatOfPoint2f(dst)));) {
            transform = Utils.to2dDoubleArray(cvmat);
        }

        sa = transform[0][0];
        sb = transform[0][1];
        sc = transform[1][0];
        sd = transform[1][1];
        tx = transform[0][2];
        ty = transform[1][2];
    }

    @Override
    public Point transform(final Point point) {
        final double x = point.x;
        final double y = point.y;
        return new Point(x * sa + y * sb + tx, x * sc + y * sd + ty);
    }
}
