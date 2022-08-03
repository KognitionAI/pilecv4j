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

public class ControlPoints {
    public final ControlPoint[] controlPoints;

    @SuppressWarnings("unused")
    private ControlPoints() {
        controlPoints = null;
    }

    public ControlPoints(final ControlPoint[] controlPoints) {
        this.controlPoints = controlPoints;
    }
}
