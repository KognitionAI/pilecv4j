package ai.kognition.pilecv4j.ffmpeg;

import java.util.Optional;
import java.util.function.LongPredicate;

import org.slf4j.Logger;

/**
 * This can be used to throttle a frame source to a particular FPS. The filter will return false
 * for every frame's timestamp when that frame should be dropped and true when it should
 * be included. Including only frames that return true will provide a stream of frames with
 * approximately the desired FPS.
 */
public class Throttle implements LongPredicate {

    public static final long FRAMERATE_MONITOR_PERIOD_MILLIS = 10000;

    public static enum TimingType {
        /**
         * The time is the frames per second
         */
        FPS {
            @Override
            long toPeriodMillis(final int time) {
                return Math.round((1.0D / time) * 1000D);
            }
        },

        /**
         * The time is the period in milliseconds
         */
        PERIOD;

        long toPeriodMillis(final int time) {
            return time;
        }
    }

    private final long periodMillis;
    private final Logger logFps;
    private final FpsMonitor incomingFramerate;
    private final FpsMonitor outgoingFramerate;
    private final boolean monitoringFramerate;

    private long expectedTimeOfNextFrame = 0;

    public Throttle(final int time, final TimingType tt, final Logger logFps) {
        this.periodMillis = tt.toPeriodMillis(time);
        this.logFps = logFps;
        if(logFps != null && logFps.isInfoEnabled()) {
            incomingFramerate = new FpsMonitor(FRAMERATE_MONITOR_PERIOD_MILLIS);
            outgoingFramerate = new FpsMonitor(FRAMERATE_MONITOR_PERIOD_MILLIS);
            monitoringFramerate = true;
        } else {
            incomingFramerate = null;
            outgoingFramerate = null;
            monitoringFramerate = false;
        }
    }

    /**
     * This should be called on every frame in order. It will return false
     * for every frame's timestamp when that frame should be dropped and true when it should
     * be included. Including only frames that return true will provide a stream of frames with
     * approximately the desired FPS.
     *
     * @return whether or not to pass the frame on.
     */
    public boolean include(final long timestamp) {
        if(monitoringFramerate)
            Optional.ofNullable(incomingFramerate).map(fr -> fr.apply(timestamp)).ifPresent(d -> logFps.info("Source Frame Rate: " + d));

        System.out.println("is " + timestamp + " >= " + expectedTimeOfNextFrame);
        if(timestamp >= expectedTimeOfNextFrame) {
            // we need to update expectedTimeOfNextFrame based on the last expectedTimeOfNextFrame
            if(expectedTimeOfNextFrame == 0) // if we just kicked off this process, then initialize to the current frame
                expectedTimeOfNextFrame = timestamp + periodMillis;
            else {
                // adjust to the next period
                expectedTimeOfNextFrame += periodMillis;
//                if(expectedTimeOfNextFrame <= timestamp) // we need to reset since more than a period went by since the last frame.
//                    expectedTimeOfNextFrame = timestamp + periodMillis;
            }

            if(monitoringFramerate)
                Optional.ofNullable(outgoingFramerate).map(fr -> fr.apply(timestamp)).ifPresent(d -> logFps.info("Outgoing Frame Rate: " + d));

            System.out.println("true");
            return true;
        } else {
            System.out.println("false");
            return false;
        }
    }

    /**
     * Just calls {@link Throttle#include(long)}
     */
    @Override
    public boolean test(final long value) {
        return include(value);
    }
}
