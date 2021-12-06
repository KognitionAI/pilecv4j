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
