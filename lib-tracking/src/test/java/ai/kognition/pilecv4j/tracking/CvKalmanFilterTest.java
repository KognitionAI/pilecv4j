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

import static org.junit.Assert.assertEquals;
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

    /**
     * Regression test: {@link CvKalmanFilter#getPosterioriErrorEstimateCovariance()} used to return the measurement
     * noise covariance (R, measureParameters square) instead of errorCovPost (P(k), dynamicParameters square). Besides
     * returning the wrong matrix, this made {@link CvKalmanFilter#setPosterioriErrorEstimateCovariance(org.opencv.core.Mat)}
     * reject every correctly-sized matrix whenever dynamicParameters != measureParameters.
     */
    @Test
    public void posterioriErrorCovarianceIsErrorCovPost() {
        try(final CvKalmanFilter kf = new CvKalmanFilter(4, 2, 0, KalmanDataType.CV_32F)) {
            try(final CvMat post = kf.getPosterioriErrorEstimateCovariance()) {
                assertNotNull(post);
                assertEquals(kf.dynamicParameters, post.rows());
                assertEquals(kf.dynamicParameters, post.cols());
            }

            // the setter must accept a correctly-sized [dynamicParameters x dynamicParameters] matrix ...
            try(final CvMat p = CvMat.identity(4, 4, KalmanDataType.CV_32F.cvType, new org.opencv.core.Scalar(7))) {
                kf.setPosterioriErrorEstimateCovariance(p);
            }
            // ... and it must round-trip through the getter.
            try(final CvMat roundtrip = kf.getPosterioriErrorEstimateCovariance()) {
                assertEquals(7d, roundtrip.get(0, 0)[0], 1e-6);
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
