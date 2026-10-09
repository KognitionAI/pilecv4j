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

package ai.kognition.pilecv4j.image;

import static ai.kognition.pilecv4j.image.CvMat.TRACK_MEMORY_LEAKS;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CvMatOfPoint2f extends MatOfPoint2f implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvMatOfPoint2f.class);

    private boolean skipCloseOnceForReturn = false;

    static {
        CvMat.initOpenCv();
    }

    private static final Method nDelete;
    private boolean deletedAlready = false;

    protected final RuntimeException stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    protected RuntimeException delStackTrace = null;

    // Cleaner-based leak tracking (replaces the deprecated finalize()-based tracking, JEP-421).
    private final ImageAPI.LeakGuard leakGuard = new ImageAPI.LeakGuard(getClass().getSimpleName(), stackTrace);
    private final java.lang.ref.Cleaner.Cleanable cleanable = ImageAPI.registerLeakGuard(this, leakGuard);

    static {
        try {
            nDelete = org.opencv.core.Mat.class.getDeclaredMethod("n_delete", long.class);
            nDelete.setAccessible(true);
        } catch(final NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(
                "Got an exception trying to access Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.", e);
        }
    }

    protected CvMatOfPoint2f(final long nativeObj) {
        super(nativeObj);
    }

    public CvMatOfPoint2f() {
    }

    public CvMatOfPoint2f(final Point... a) {
        super(a);
    }

    public CvMatOfPoint2f(final Mat mat) {
        super(mat);
    }

    /**
     * Shallow copy this as a CvMat.
     *
     * @param flatten the number of channels present in this correspond to the dimensionality of the point, i.e. the shape is (rows=numPoints, cols=1,
     *     channels=numDimensions). If flatten is true (and transpose is not), instead, return a mat with shape of (rows=numPoints, cols=numDimensions,
     *     channels=1)- which is a more traditional format for most mathematical operations.
     * @param transpose return the mat transposed from its original shape.
     *
     * @return <em>The caller owns the returned Mat.</em>
     */
    public CvMat asCvMat(final boolean flatten, final boolean transpose) {
        try(final var mat = CvMat.shallowCopy(this);
            // shaped can be the actual mat, and to avoid closing it twice,
            // returnMe is called.
            final var shaped = flatten ? CvMat.move(mat.reshape(1)) : mat.returnMe();) {

            if(transpose)
                try(final var mat_t = shaped.t();) {
                    return mat_t.returnMe();
                }
            else
                return shaped.returnMe();
        }
    }

    public static CvMatOfPoint2f move(final MatOfPoint mat) {
        return new CvMatOfPoint2f(ImageAPI.pilecv4j_image_CvRaster_move(mat.nativeObj));
    }

    public CvMatOfPoint2f returnMe() {
        skipCloseOnceForReturn = true;
        return this;
    }

    @Override
    public void close() {
        if(!skipCloseOnceForReturn) {
            if(!deletedAlready) {
                doNativeDelete();
                deletedAlready = true;
                leakGuard.markClosed();
                cleanable.clean();
                if(TRACK_MEMORY_LEAKS) {
                    delStackTrace = new RuntimeException("Here's where I was closed");
                }
            } else if(TRACK_MEMORY_LEAKS) {
                LOGGER.warn("TRACKING: Deleting {} again at:", this.getClass()
                    .getSimpleName(), new RuntimeException());
                LOGGER.warn("TRACKING: originally closed at:", delStackTrace);
                LOGGER.warn("TRACKING: create at: ", stackTrace);
            }
        } else
            skipCloseOnceForReturn = false; // next close counts.
    }

    protected void doNativeDelete() {
        try {
            nDelete.invoke(this, super.nativeObj);
        } catch(final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(
                "Got an exception trying to call Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.", e);
        }
    }

    // This EMPTY finalize() override is deliberate: OpenCV's Mat.finalize() calls n_delete()
    // unconditionally, which would double-free the native Mat for every properly closed
    // instance. An empty (trivial) finalizer is recognized by the JVM so instances are NOT
    // registered for finalization. Leak tracking is handled by the Cleaner (ImageAPI.LeakGuard).
    @SuppressWarnings({"deprecation","removal"})
    @Override
    protected final void finalize() throws Throwable {}
}
