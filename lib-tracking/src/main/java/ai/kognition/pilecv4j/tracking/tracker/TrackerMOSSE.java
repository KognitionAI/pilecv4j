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

package ai.kognition.pilecv4j.tracking.tracker;

import static ai.kognition.pilecv4j.image.CvMat.TRACK_MEMORY_LEAKS;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.opencv.core.Mat;
import org.opencv.core.Rect2d;
import org.opencv.tracking.legacy_TrackerMOSSE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.tracking.Tracker;

/**
 * Extension of OpenCV's implementation of the Minimum Output of Sum of Squard Error (MOSSE) filter to fit {@link Tracker}.
 *
 * @see <a href="https://www.cs.colostate.edu/~vision/publications/bolme_cvpr10.pdf">cs.colorado-state: Visual Object Tracking using Adaptive Correlation
 * Filters</a>
 */
public class TrackerMOSSE extends legacy_TrackerMOSSE implements Tracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerMOSSE.class);
    public static final boolean SUPPORTS_MASKING = false;

    private static final Method nativeCreate;
    private static final Method nativeDelete;
    protected boolean isInitialized = false;
    protected boolean deletedAlready = false;
    protected boolean skipOnceForDelete = false;

    protected final RuntimeException stackTrace;
    protected RuntimeException delStackTrace = null;

    static {
        CvMat.initOpenCv();
    }

    static {
        try {
            nativeCreate = legacy_TrackerMOSSE.class.getDeclaredMethod("create_0");
            nativeCreate.setAccessible(true);
            nativeDelete = legacy_TrackerMOSSE.class.getDeclaredMethod("delete", long.class);
            nativeDelete.setAccessible(true);
        } catch(final NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("Got an exception trying to access " + TrackerMOSSE.class.getSimpleName() +
                ".delete or .create_0. Either the security model is too restrictive or the version of OpenCv can't be supported.", e);
        }
    }

    public TrackerMOSSE() {
        this(doNativeCreate());
    }

    protected TrackerMOSSE(final long nativeAddr) {
        super(nativeAddr);
        stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated") : null;
    }

    @Override
    public boolean supportsMasking() {
        return SUPPORTS_MASKING;
    }

    @Override
    public boolean isInitialized() {
        return isInitialized;
    }

    @Override
    public Optional<Tracker> initialize(final Mat image, final Rect2d initialBoundingBox) {
        if(!super.init(image, initialBoundingBox)) {
            this.close();
            return Optional.empty();
        }
        isInitialized = true;
        return Optional.of(this);
    }

    @Override
    public Optional<Rect2d> update(final Mat image) {
        final Rect2d newBoundingBox = new Rect2d();
        if(!super.update(image, newBoundingBox))
            return Optional.empty();
        return Optional.of(newBoundingBox);
    }

    protected static long doNativeCreate() {
        try {
            return (Long)nativeCreate.invoke(null);
        } catch(final IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Got an exception trying to call Tracker.create_0. Either the security model is too restrictive or the version of " +
                "OpenCV can't be supported.", e);
        }
    }

    protected void doNativeDelete() {
        try {
            nativeDelete.invoke(this, super.nativeObj);
        } catch(final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException("Got an exception trying to call Tracker.delete. Either the security model is too restrictive or the version of " +
                "OpenCV can't be supported.", e);
        }
    }

    @Override
    public TrackerMOSSE skipOnceForReturn() {
        skipOnceForDelete = true;
        return this;
    }

    @Override
    public void close() {
        if(!skipOnceForDelete) {
            if(!deletedAlready) {
                doNativeDelete();
                deletedAlready = true;
                if(TRACK_MEMORY_LEAKS)
                    delStackTrace = new RuntimeException("Here's where I was closed");
            } else if(TRACK_MEMORY_LEAKS) {
                LOGGER.warn("TRACKING: deleting {} again at:", this.getClass()
                    .getSimpleName(), new RuntimeException());
                LOGGER.warn("TRACKING: originally closed at:", delStackTrace);
                LOGGER.warn("TRACKING: create at: ", stackTrace);
            }
        } else {
            skipOnceForDelete = false;
        }
    }

    @Override
    public void finalize() {
        if(!deletedAlready) {
            LOGGER.debug("Finalizing a {} that hasn't been closed.", this.getClass()
                .getSimpleName());
            if(TRACK_MEMORY_LEAKS)
                LOGGER.debug("TRACKING: here's where I was instantiated: ", stackTrace);
            close();
        }
    }

    @Override
    public String toString() {
        return "TrackerMOSSE{" + "isInitialized=" + isInitialized + ", deletedAlready=" + deletedAlready + '}';
    }
}
