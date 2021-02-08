package ai.kognition.pilecv4j.tracking;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.tracking.CvKalmanFilter.KalmanDataType;

public class CvKalmanFilterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvKalmanFilterTest.class);

    private static boolean isShowSet() {
        final String sysOpSHOW = System.getProperty("pilecv4j.SHOW");
        final boolean sysOpSet = sysOpSHOW != null;
        boolean show = ("".equals(sysOpSHOW) || Boolean.parseBoolean(sysOpSHOW));
        if(!sysOpSet)
            show = Boolean.parseBoolean(System.getenv("PILECV4J_SHOW"));
        return show;
    }

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
