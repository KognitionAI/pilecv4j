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

import org.opencv.core.Point;

public class ControlPoint {
    public final Point originalPoint;
    public final Point transformedPoint;

    @SuppressWarnings("unused")
    private ControlPoint() {
        originalPoint = null;
        transformedPoint = null;
    }

    public ControlPoint(final Point originalPoint, final Point transformedPoint) {
        this.originalPoint = originalPoint;
        this.transformedPoint = transformedPoint;
    }

    @Override
    public String toString() {
        return "ControlPoint [originalPoint=" + originalPoint + ", transformedPoint=" + transformedPoint + "]";
    }
}
