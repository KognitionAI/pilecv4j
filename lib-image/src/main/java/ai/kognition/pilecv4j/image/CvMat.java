package ai.kognition.pilecv4j.image;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class is an easier (perhaps) and more efficient interface to an OpenCV
 * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
 * than the one available through the offical Java wrapper. It includes more efficient resource
 * management as an {@link AutoCloseable} and the ability to do more <em>"zero-copy"</em>
 * image manipulations than is typically available in OpenCVs default Java API.
 * </p>
 *
 * <h2>Memory management</h2>
 *
 * <p>
 * In OpenCV's C/C++ API, the developer is responsible for managing the resources. The
 * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> class in C++
 * references the underlying memory resources for the image data. When a C++ Mat is deleted,
 * this memory is freed (that is, as long as other Mat's aren't referring to the same
 * memory, in which case when the last one is deleted, the memeory is freed). This gives the
 * developer using the C++ API fine grained control over the compute resources.
 * </p>
 *
 * <p>
 * However, for Java developers, it's not typical for the developer to manage memory or explicitly
 * delete objects or free resources. Instead, they typically rely on garbage collection. The
 * problem with doing that in OpenCV's Java API is that the Java VM and it's garbage
 * collector <em>can't see</em> the image memory referred to by the
 * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>. This memory is
 * <a href="https://stackoverflow.com/questions/6091615/difference-between-on-heap-and-off-heap"><em>off-heap</em></a>
 * from the perspective of the Java VM.
 * </p>
 *
 * <p>
 * This is why, as you may have experienced if you've used OpenCV's Java API in a larger
 * video system, you can rapidly run out of memory. Creating a Mat for each high resolution
 * video frame but lettingthe JVM garbage collector decide when to delete these objects as
 * you create will eventually (usually rapidly) fill the available system memory since the
 * garbage collector is unaware of how much of that computer memory is actually being
 * utilized by these <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>s.
 * </p>
 *
 * <p>
 * This class allows OpenCV's <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>s
 * to be managed the same way you would any other {@link AutoCloseable} in Java (since 1.7).
 * That is, using a <em>"try-with-resource"</em>.
 * </p>
 *
 * <h3>Tracking memory leaks</h3>
 *
 * Additionally, you can track leaks in your use of {@link CvMat} by setting the environment variable
 * {@code PILECV4J_TRACK_MEMORY_LEAKS="true"} or by using the system property
 * {@code -Dpilecv4j.TRACK_MEMORY_LEAKS=true}. This will tell {@link CvMat} to track the locations in
 * the code where it's been instantiated so that if it's eventually deleted by the garbage collector,
 * rather than {@code CvMat#close}d by the developer, a {@code debug} level log message will be emitted
 * identifying where the leaked {@link CvMat} was initially instantiated.
 */
public class CvMat extends Mat implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvMat.class);
    private static final boolean TRACK_MEMORY_LEAKS;
    private boolean skipCloseOnceForReturn = false;

    static {
        ImageAPI._init();

        final String sysOpTRACKMEMLEAKS = System.getProperty("pilecv4j.TRACK_MEMORY_LEAKS");
        final boolean sysOpSet = sysOpTRACKMEMLEAKS != null;
        boolean track = ("".equals(sysOpTRACKMEMLEAKS) || Boolean.parseBoolean(sysOpTRACKMEMLEAKS));
        if(!sysOpSet)
            track = Boolean.parseBoolean(System.getenv("PILECV4J_TRACK_MEMORY_LEAKS"));

        TRACK_MEMORY_LEAKS = track;
    }

    public static void initOpenCv() {}

    // This is used when there's an input matrix that can't be null but should be ignored.
    public static final Mat nullMat = new Mat();

    private static final Method nDelete;
    private static final Field nativeObjField;

    private boolean deletedAlready = false;

    private final RuntimeException stackTrace;

    static {
        try {
            nDelete = org.opencv.core.Mat.class.getDeclaredMethod("n_delete", long.class);
            nDelete.setAccessible(true);

            nativeObjField = org.opencv.core.Mat.class.getDeclaredField("nativeObj");
            nativeObjField.setAccessible(true);
        } catch(NoSuchMethodException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(
                "Got an exception trying to access Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.",
                e);
        }
    }

    private CvMat(final long nativeObj) {
        super(nativeObj);
        stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    /**
     * Construct's an empty {@link CvMat}.
     * This simply calls the parent classes equivalent constructor.
     */
    public CvMat() {
        stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    /**
     * Construct a {@link CvMat} and preallocate the image space.
     * This simply calls the parent classes equivalent constructor.
     *
     * @param rows number of rows
     * @param cols number of columns
     * @param type type of the {@link CvMat}. See
     *            <a href="https://docs.opencv.org/4.0.1/javadoc/org/opencv/core/CvType.html">CvType</a>
     */
    public CvMat(final int rows, final int cols, final int type) {
        super(rows, cols, type);
        stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    /**
     * Construct a {@link CvMat} and preallocate the image space and fill it from the {@link ByteBuffer}.
     * This simply calls the parent classes equivalent constructor.
     *
     * @param rows number of rows
     * @param cols number of columns
     * @param type type of the {@link CvMat}. See
     *            <a href="https://docs.opencv.org/4.0.1/javadoc/org/opencv/core/CvType.html">CvType</a>
     * @param data the {@link ByteBuffer} with the image date.
     */
    public CvMat(final int rows, final int cols, final int type, final ByteBuffer data) {
        super(rows, cols, type, data);
        stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    /**
     * This performs a proper matrix multiplication that returns {@code this * other}.
     *
     * @return a new {@link CvMat} resulting from the operation. <b>Note: The caller owns the CvMat returned</b>
     *
     * @see <a href=
     *      "https://docs.opencv.org/4.0.1/d2/de8/group__core__array.html#gacb6e64071dffe36434e1e7ee79e7cb35">cv::gemm()</a>
     */
    public CvMat mm(final Mat other) {
        return mm(other, 1.0D);
    }

    /**
     * This performs a proper matrix multiplication and multiplies the result by a scalar. It returns:
     * <p>
     * {@code scale (this * other)}.
     *
     * @return a new {@link CvMat} resulting from the operation. <b>Note: The caller owns the CvMat returned</b>
     *
     * @see <a href=
     *      "https://docs.opencv.org/4.0.1/d2/de8/group__core__array.html#gacb6e64071dffe36434e1e7ee79e7cb35">cv::gemm()</a>
     */
    public CvMat mm(final Mat other, final double scale) {
        final Mat ret = new Mat();
        Core.gemm(this, other, scale, nullMat, 0.0D, ret);
        return CvMat.move(ret);
    }

    /**
     * Apply the given {@link Function} to a {@link CvRaster} containing the image data for this {@link CvMat}
     *
     * @param function is the {@link Function} to pass the {@link CvRaster} to.
     * @return the return value of the provided {@code function}
     * @see CvRaster
     */
    public <T> T rasterOp(final Function<CvRaster, T> function) {
        try (final CvRaster raster = CvRaster.makeInstance(this)) {
            return function.apply(raster);
        }
    }

    /**
     * Apply the given {@link Consumer} to a {@link CvRaster} containing the image data for this {@link CvMat}
     *
     * @param function is the {@link Consumer} to pass the {@link CvRaster} to.
     * @see CvRaster
     */
    public void rasterAp(final Consumer<CvRaster> function) {
        try (final CvRaster raster = CvRaster.makeInstance(this)) {
            function.accept(raster);
        }
    }

    /**
     * @return How many bytes constitute the image data.
     */
    public long numBytes() {
        return elemSize() * rows() * cols();
    }

    /**
     * Free the resources for this {@link CvMat}. Once the {@link CvMat} is closed, it shouldn't be used and certainly
     * wont contain the image data any longer.
     */
    @Override
    public void close() {
        if(!skipCloseOnceForReturn) {
            if(!deletedAlready) {
                try {
                    nDelete.invoke(this, super.nativeObj);
                } catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new RuntimeException(
                        "Got an exception trying to call Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.",
                        e);
                }
                deletedAlready = true;
            }
        } else
            skipCloseOnceForReturn = false; // next close counts.
    }

    @Override
    public String toString() {
        return "CvMat: (" + getClass().getName() + "@" + Integer.toHexString(hashCode()) + ") " + super.toString();
    }

    /**
     * Helper function for applying a {@link Function} to the a {@link CvRaster} built from the given
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     *
     * @param mat <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> to build the
     *            {@link CvRaster} from.
     * @param function is the {@link Function} to pass the {@link CvRaster} to.
     * @return the return value of the provided {@code function}
     * @see CvRaster
     */
    public static <T> T rasterOp(final Mat mat, final Function<CvRaster, T> function) {
        if(mat instanceof CvMat)
            return ((CvMat)mat).rasterOp(function);
        else {
            try (CvRaster raster = CvRaster.makeInstance(mat)) {
                return function.apply(raster);
            }
        }
    }

    /**
     * Helper function for applying a {@link Consumer} to the a {@link CvRaster} built from the given
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     *
     * @param mat <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> to build the
     *            {@link CvRaster} from.
     * @param function is the {@link Consumer} to pass the {@link CvRaster} to.
     * @see CvRaster
     */
    public static void rasterAp(final Mat mat, final Consumer<CvRaster> function) {
        if(mat instanceof CvMat)
            ((CvMat)mat).rasterAp(function);
        else {
            try (CvRaster raster = CvRaster.makeInstance(mat)) {
                function.accept(raster);
            }
        }
    }

    /**
     * This call should be made to manage a copy of the Mat using a {@link CvRaster}.
     * NOTE!! Changes to the {@link CvMat} will be reflected in the {@link Mat} and
     * vs. vrs. If you want a deep copy/clone of the original Mat then consider
     * using {@link CvMat#deepCopy(Mat)}.
     */
    public static CvMat shallowCopy(final Mat mat) {
        return new CvMat(ImageAPI.CvRaster_copy(mat.nativeObj));
    }

    /**
     * This call will manage a complete deep copy of the provided {@code Mat}.
     * Changes in one will not be reflected in the other.
     */
    public static CvMat deepCopy(final Mat mat) {
        if(mat.rows() == 0)
            return move(new Mat(mat.rows(), mat.cols(), mat.type()));
        if(mat.isContinuous())
            return move(mat.clone());

        final CvMat newMat = new CvMat(mat.rows(), mat.cols(), mat.type());
        mat.copyTo(newMat);
        return newMat;
    }

    /**
     * <p>
     * This call can be made to hand management of a
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>'s resources
     * over to a new {@link CvMat}. The
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> passed
     * in <em>SOULD NOT</em> be used after this call or, at least, it shouldn't be assumed
     * to still be pointing to the same image data. When the {@link CvMat} is closed, it will
     * release the data that was originally associated with the {@code Mat}. If you want
     * to keep the {@code Mat} beyond the life of the {@link CvMat}, then consider using
     * {@link CvMat#shallowCopy(Mat)} instead of {@link CvMat#move(Mat)}.
     * </p>
     *
     * @param mat - <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     *            to take control of with the new {@link CvMat}. After this call the
     *            <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a>
     *            passed should not be used.
     * @return a new {@link CvMat} that now managed the internal resources of the origin. <b>Note: The caller owns the
     *         CvMat returned</b>
     */
    public static CvMat move(final Mat mat) {
        if(mat == null)
            return null;

        final long defaultMatNativeObj = ImageAPI.CvRaster_defaultMat();
        try {
            final long nativeObjToUse = mat.nativeObj;
            nativeObjField.set(mat, defaultMatNativeObj);
            return new CvMat(nativeObjToUse);
        } catch(final IllegalAccessException e) {
            throw new RuntimeException(
                "Got an exception trying to set Mat.nativeObj. Either the security model is too restrictive or the version of OpenCv can't be supported.",
                e);
        }
    }

    /**
     * Convenience method that wraps the return value of <a href=
     * "https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html#a0b57b6a326c8876d944d188a46e0f556">{@code Mat.zeros}</a>
     * in a {@link CvMat}.
     *
     * @param rows number of rows of in the resulting {@link CvMat}
     * @param cols number of columns of in the resulting {@link CvMat}
     * @param type type of the resulting {@link CvMat}. See
     *            <a href="https://docs.opencv.org/4.0.1/javadoc/org/opencv/core/CvType.html">CvType</a>
     * @return a new {@link CvMat} with all zeros of the given proportions and type. <b>Note: The caller owns the CvMat
     *         returned</b>
     */
    public static CvMat zeros(final int rows, final int cols, final int type) {
        return CvMat.move(Mat.zeros(rows, cols, type));
    }

    /**
     * This implements {@code leftOp = rightOp}
     */
    public static void reassign(final Mat leftOp, final Mat rightOp) {
        ImageAPI.CvRaster_assign(leftOp.nativeObj, rightOp.nativeObj);
    }

    /**
     * You can use this method to create a {@link CvMat}
     * given a native pointer to the location of the raw data, and the metadata for the
     * {@code Mat}. Since the data is being passed to the underlying {@code Mat}, the {@code Mat}
     * will not be the "owner" of the data. That means YOU need to make sure that the native
     * data buffer outlives the {@link CvMat} or you're pretty much guaranteed a core dump.
     */
    public static CvMat create(final int rows, final int cols, final int type, final long pointer) {
        final long nativeObj = ImageAPI.CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
        if(nativeObj == 0)
            throw new NullPointerException("Cannot create a CvMat from a null pointer data buffer.");
        return CvMat.wrapNative(nativeObj);
    }

    /**
     * This method allows the developer to return a {@link CvMat} that's being managed by
     * a <em>"try-with-resource"</em> without worrying about the {@link CvMat}'s resources
     * being freed. As an example:
     *
     * <pre>
     * <code>
     *   try (CvMat matToReturn = new CvMat(); ) {
     *      // do something to fill in the matToReturn
     *
     *      return matToReturn.returnMe();
     *   }
     * </code>
     * </pre>
     *
     * <p>
     * While it's possible to simply not use a try-with-resource and leave the {@link CvMat} unmanaged,
     * you run the possibility of leaking the {@link CvMat} if an exception is thrown prior to returning
     * it.
     * </p>
     * 
     * <p>
     * Note: if you call {@link CvMat#returnMe()} and don't actually reassign the result to another managed
     * {@link CvMat}, you will leak the CvMat.
     * </p>
     */
    public CvMat returnMe() {
        // hacky, yet efficient.
        skipCloseOnceForReturn = true;
        return this;
    }

    /**
     * <p>
     * Creates a {@link CvMat} given a handle to a native C++
     * <a href="https://docs.opencv.org/4.0.1/d3/d63/classcv_1_1Mat.html">Mat</a> instance.
     * nativeObj needs to be a native pointer to a C++ cv::Mat object or you're likely to
     * get a core dump. The management of that Mat will now be the responsibility
     * of the CvMat. If something else ends up invoking the destructor on the native
     * cv::Mat then there will likely be a core dump when subsequently using the {@link CvMat}
     * returned. This includes even the deletion of the {@link CvMat} by the garbage collector.
     * </p>
     * 
     * <p>
     * <em>With great power, comes great responsibility.</em>
     * </p>
     *
     * @param nativeObj - pointer to a C++ cv::Mat instance. You're on your own as to how to
     *            obtain one of these but you will likely need to write C++ code to do it.
     */
    public static CvMat wrapNative(final long nativeObj) {
        return new CvMat(nativeObj);
    }

    // Prevent Mat finalize from being called
    @Override
    protected void finalize() throws Throwable {
        if(!deletedAlready) {
            LOGGER.debug("Finalizing a {} that hasn't been closed.", CvMat.class.getSimpleName());
            if(TRACK_MEMORY_LEAKS)
                LOGGER.debug("Here's where I was instantiated: ", stackTrace);
            close();
        }
    }
}
