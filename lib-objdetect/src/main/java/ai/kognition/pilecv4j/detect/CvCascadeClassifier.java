package ai.kognition.pilecv4j.detect;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvMat;

/**
 * <p>
 * This class is an easier interface to an OpenCV <a href="https://docs.opencv.org/4.1.2/d1/de5/classcv_1_1CascadeClassifier.html">CasacdeClassifier</a> than
 * the one available through the official Java wrapper. It includes more efficient resource management as an {@link AutoCloseable} and utility methods than is
 * typically available in OpenCVs default Java API. Unlike {@link CvMat}, however, the focus is on convenience and memory safety rather than performance.</p>
 *
 * @see ai.kognition.pilecv4j.image.CvMat
 */
public class CvCascadeClassifier extends CascadeClassifier implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvCascadeClassifier.class);
    private static final boolean TRACK_MEMORY_LEAKS;
    private static final Method nativeDelete;

    static {
        final String sysOpTRACKMEMLEAKS = System.getProperty("pilecv4j.TRACK_MEMORY_LEAKS");
        final boolean sysOpSet = sysOpTRACKMEMLEAKS != null;
        boolean track = ("".equals(sysOpTRACKMEMLEAKS) || Boolean.parseBoolean(sysOpTRACKMEMLEAKS));
        if(!sysOpSet)
            track = Boolean.parseBoolean(System.getenv("PILECV4J_TRACK_MEMORY_LEAKS"));

        TRACK_MEMORY_LEAKS = track;
    }

    private final RuntimeException stackTrace;
    private boolean hasModelLoaded = false;
    private boolean deletedAlready = false;
    private boolean skipDelete = false;

    static {
        try {
            nativeDelete = org.opencv.objdetect.CascadeClassifier.class.getDeclaredMethod("delete", long.class);
            nativeDelete.setAccessible(true);
        } catch(final NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(
                "Got an exception trying to access Mat.n_Delete. Either the security model is too restrictive or the version of OpenCv can't be supported.", e);
        }
    }

    public CvCascadeClassifier() {
        super();
        stackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("Here's where I was instantiated: ") : null;
    }

    /**
     * @param modelUri Cascade classifiers require models to be run. A cascade classifier without a model cannot operate.
     *
     * @throws IllegalArgumentException when the model is unable to be found or loaded.
     * @throws IllegalStateException when attempting to load a model if the classifier already has a model.
     */
    public void loadModel(final File modelUri) {
        if(hasModelLoaded) {
            throw new IllegalStateException("Attempted to load a model when a model is already present.");
        }
        if(deletedAlready) {
            throw new IllegalStateException("Attempted to load model when classifier is deleted.");
        }
        if(!modelUri.exists()) {
            throw new IllegalArgumentException("Unable to find model.");
        }
        if(!super.load(modelUri.toString())) {
            throw new IllegalArgumentException("Unable to load given model.");
        }
        hasModelLoaded = true;
    }

    public boolean hasModelLoaded() {
        return hasModelLoaded;
    }

    /**
     * @see CvMat#returnMe()
     */
    public CvCascadeClassifier returnMe() {
        skipDelete = true;
        return this;
    }

    @Override
    public void close() {
        if(!skipDelete) {
            if(!deletedAlready)
                try {
                    nativeDelete.invoke(this, super.nativeObj);
                } catch(final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new RuntimeException(
                        "Got an exception trying to call CascadeClassifier.delete. Either the security model is too restrictive or the version of OpenCv can't " +
                            "be supported.", e);
                }
            deletedAlready = true;
        } else {
            skipDelete = false;
        }
    }

    @Override
    protected void finalize() {
        if(!deletedAlready) {
            LOGGER.debug("Finalizing a {} that hasn't been closed.", CvMat.class.getSimpleName());
            if(TRACK_MEMORY_LEAKS)
                LOGGER.debug("Here's where I was instantiated: ", stackTrace);
            close();
        }
    }

    @Override
    public String toString() {
        return "CvCascadeClassifier{hasModelLoaded=" + hasModelLoaded + ", deletedAlready=" + deletedAlready + "} " + super.toString();
    }
}
