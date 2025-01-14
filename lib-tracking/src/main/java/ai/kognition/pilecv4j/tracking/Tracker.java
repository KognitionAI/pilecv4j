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

import java.util.Optional;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;

/**
 * Much like {@link ai.kognition.pilecv4j.image.CvMat}, this is an easier interface to OpenCV's tracking ({@link org.opencv.tracking.Tracker}) class. It has
 * simple memory management via the {@link AutoCloseable} interface and builder patterns.
 */
public interface Tracker extends AutoCloseable {
    boolean supportsMasking();

    default Tracker setMask(final Mat mask) {
        throw new UnsupportedOperationException(this.getClass()
            .getSimpleName() + " does not support masking.");
    }

    boolean isInitialized();

    /**
     * All trackers in OpenCV must be initialized by first stating "this is the object I wish to track."
     * <p>
     * Not all trackers will be able to understand what the underlying features are to be able to discriminate the difference between the object and the
     * background. The myriad of ways in which a tracker can fail to initialize are numerous but the end result is the same- a tracker that doesn't track. For
     * all trackers, <em>in the event that the tracker fails to initialize, the underlying memory will be automatically released via the {@link this#close()}
     * method.</em>
     *
     * @param image image from which the bounding box was pulled from
     * @param initialBoundingBox The object you wish to track
     *
     * @return {@link Optional#empty()} if the tracker fails to initialize. Otherwise, return this.
     */
    Optional<Tracker> initialize(Mat image, Rect2d initialBoundingBox);

    default Optional<Tracker> initialize(final Mat image, final Rect initialBoundingBox) {
        return initialize(image, new Rect2d(initialBoundingBox.x, initialBoundingBox.y, initialBoundingBox.width, initialBoundingBox.height));
    }

    /**
     * Update the tracker and find the new, most-likely bounding box for the target.
     *
     * @param image Current frame.
     *
     * @return {@link Optional#empty()} if the target was not located. Otherwise, return the bounding box. Note: an empty optional does not mean that the
     * tracker has failed but merely that the target was not found in the frame. This can happen if the target is out of sight.
     */
    Optional<Rect2d> update(Mat image);

    /**
     * This method allows the developer to return a {@link Tracker} that's being managed by a <em>"try-with-resource"</em> without worrying about the resources
     * being freed. As an example:
     *
     * <pre>
     * <code>
     *   try(final Tracker trackerToReturn = new Tracker()) {
     *       // attempt initialization
     *       return trackerToReturn.skipOnceForReturn();
     *   }
     * </code>
     * </pre>
     *
     * <p>
     * While it's possible to simply not use a try-with-resource and leave the {@link Tracker} unmanaged, you run the possibility of leaking it if an exception
     * is thrown prior to returning it.
     *
     * @return the tracker
     */
    Tracker skipOnceForReturn();
}
