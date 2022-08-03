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

package ai.kognition.pilecv4j.image.geometry;

import java.util.Comparator;

public class WeightedPointComparator implements Comparator<WeightedPoint> {
    public WeightedPointComparator() {}

    @Override
    public int compare(final WeightedPoint o1, final WeightedPoint o2) {
        final double diff = (o2.getWeight() - o1.getWeight());
        return diff > 0 ? 1 : (diff == 0.0D ? 0 : -1);
    }
}
