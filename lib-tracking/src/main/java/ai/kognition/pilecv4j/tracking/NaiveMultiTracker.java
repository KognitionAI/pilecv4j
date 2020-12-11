package ai.kognition.pilecv4j.tracking;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Rect2d;

/**
 * This is a reimplementation of {@link org.opencv.tracking.MultiTracker} but written in Java for Java.
 * <p>
 * This works exactly in the same way that the C++ MultiTrackerAlt operates- naively- but allows the developer to have easy, underlying access to the internal
 * trackers, and to be able to remove them.
 */
public class NaiveMultiTracker {
    public final List<Tracker> trackers = new ArrayList<>();

    /**
     * @param tracker any previously initialized tracker.
     *
     * @see Tracker#initialize(Mat, Rect2d)
     */
    public NaiveMultiTracker addInitializedTracker(final Tracker tracker) {
        if(!tracker.isInitialized())
            throw new IllegalStateException("Attempted to pass in a " + tracker.getClass()
                .getSimpleName() + " that was not initialized.");
        trackers.add(tracker);
        return this;
    }

    /**
     * For each tracker present in this, return where the tracked object is.
     */
    public Rect2d[] update(final Mat newImage) {
        return trackers.stream()
            .map(tracker -> tracker.update(newImage))
            .map(boundingBox -> boundingBox.orElse(null))
            .toArray(Rect2d[]::new);
    }
}
