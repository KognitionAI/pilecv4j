package ai.kognition.pilecv4j.ffmpeg;

public class FpsMonitor {

    public final long periodMillis;

    private long periodStart = -1;
    private long count = -1;
    private long periodEnd = -1;

    public FpsMonitor(final long periodMillis) {
        this.periodMillis = periodMillis;
    }

    /**
     * This should be called on every frame in order. Once the period
     * passes the apply will return a non-null which represents the
     * FPS over the last period. Most of the time this will return null.
     */
    public Double apply(final long timestamp) {
        if(timestamp >= periodEnd) {
            if(periodStart > 0) {
                // do the calculation and return
                final double ret = ((double)count / (double)(periodEnd - periodStart)) * 1000D;
                reset(timestamp);
                return ret;
            }
            reset(timestamp);
            return null;
        } // otherwise
        count++;
        return null;
    }

    private void reset(final long timestamp) {
        periodStart = timestamp;
        count = 1;
        periodEnd = timestamp + periodMillis;
    }
}
