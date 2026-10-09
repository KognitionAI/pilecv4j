package ai.kognition.pilecv4j.image;

import java.util.LinkedList;
import java.util.List;

import org.opencv.core.Mat;

import net.dempsy.util.QuietCloseable;

/**
 * Manage resources from a single place
 */
public class Closer implements AutoCloseable {
    private final List<AutoCloseable> toClose = new LinkedList<>();

    public <T extends AutoCloseable> T add(final T mat) {
        if(mat != null)
            toClose.add(0, mat);
        return mat;
    }

    public <T extends Mat> T addMat(final T mat) {
        if(mat == null)
            return null;
        if(mat instanceof AutoCloseable)
            add((AutoCloseable)mat);
        else
            toClose.add(0, (QuietCloseable)() -> CvMat.closeRawMat(mat));
        return mat;
    }

    @Override
    public void close() {
        // close EVERY resource even if one of them throws (a stream forEach would stop at
        // the first failure, leaking the rest); collect subsequent failures as suppressed,
        // mirroring try-with-resources semantics.
        RuntimeException failure = null;
        for(final AutoCloseable r: toClose) {
            try {
                r.close();
            } catch(final Exception e) {
                if(failure == null)
                    failure = (e instanceof RuntimeException) ? (RuntimeException)e : new RuntimeException(e);
                else
                    failure.addSuppressed(e);
            }
        }
        toClose.clear();
        if(failure != null)
            throw failure;
    }
}
