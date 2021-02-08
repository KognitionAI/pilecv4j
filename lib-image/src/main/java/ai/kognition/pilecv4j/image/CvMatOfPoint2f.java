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

    protected final RuntimeException stackTrace;
    protected RuntimeException delStackTrace = null;

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
        this.stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    public CvMatOfPoint2f() {
        this.stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    public CvMatOfPoint2f(final Point... a) {
        super(a);
        this.stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    public CvMatOfPoint2f(final Mat mat) {
        super(mat);
        this.stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
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
            final var shaped = flatten ? CvMat.move(mat.reshape(1)) : mat.returnMe()) {

            if(transpose)
                try(final var mat_t = shaped.t()) {
                    return mat_t.returnMe();
                }
            else
                return shaped.returnMe();
        }
    }

    public static CvMatOfPoint2f move(final MatOfPoint mat) {
        return new CvMatOfPoint2f(ImageAPI.CvRaster_move(mat.nativeObj));
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

    // Prevent Mat finalize from being called
    @Override
    protected void finalize() throws Throwable {
        if(!deletedAlready) {
            LOGGER.warn("Finalizing a {} that hasn't been closed.", this.getClass()
                .getSimpleName());
            if(TRACK_MEMORY_LEAKS)
                LOGGER.warn("TRACKING: Here's where I was instantiated: ", stackTrace);
            close();
        }
    }
}
