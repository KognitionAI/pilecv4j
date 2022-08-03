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

package ai.kognition.pilecv4j.tracking;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.tracking.CvKalmanFilter.KalmanDataType;

public class CvKalmanFilterTest {
    @Test
    public void canInit() {
        try(final CvKalmanFilter kf1 = new CvKalmanFilter(1, 1, 0, KalmanDataType.CV_32F)) {

            try(kf1) {
                kf1.skipOnceForReturn();
            }

            try(final CvMat get = kf1.getGain()) {
                assertNotNull(get);
                CvKalmanFilter.dump(kf1);
            }
        }
    }

    @Test(expected = ArithmeticException.class)
    public void cannotSetBadSize() {
        try(final CvKalmanFilter kf = new CvKalmanFilter(2, 1, 0, KalmanDataType.CV_32F);
            final CvMat newGain = CvMat.zeros(3, 3, KalmanDataType.CV_32F.cvType)) {
            kf.setGain(newGain);
        }
    }
}
