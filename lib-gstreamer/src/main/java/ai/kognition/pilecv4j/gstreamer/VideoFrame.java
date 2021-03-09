package ai.kognition.pilecv4j.gstreamer;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;

public class VideoFrame extends CvMat {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoFrame.class);

    public long decodeTimeMillis;

    private final Pool pool;
    private boolean isInPool = false;
    private RuntimeException rtpStackTrace = null;
    private boolean skipCloseOnceForReturn = false;

    long frameNumber;

    public VideoFrame(final long nativeObj, final long decodeTimeMillis, final long frameNumber) {
        super(nativeObj);
        this.pool = null;
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
    }

    public VideoFrame(final long decodeTimeMillis, final long frameNumber) {
        super();
        this.pool = null;
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
    }

    private VideoFrame(final Pool pool, final int h, final int w, final int type, final long decodeTimeMillis, final long frameNumber) {
        super(h, w, type);
        this.pool = pool;
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
    }

    public static VideoFrame create(final int rows, final int cols, final int type, final long pointer, final long decodeTimeMillis, final long frameNumber) {
        final long nativeObj = ImageAPI.CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
        if(nativeObj == 0)
            throw new NullPointerException("Cannot create a CvMat from a null pointer data buffer.");
        return VideoFrame.wrapNativeVideoFrame(nativeObj, decodeTimeMillis, frameNumber);
    }

    private VideoFrame leavingPool(final long decodeTimeMillis, final long frameNumber) {
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
        if(TRACK_MEMORY_LEAKS) {
            rtpStackTrace = null;
        }
        isInPool = false;
        return this;
    }

    public static class Pool implements AutoCloseable {
        public final int h;
        public final int w;
        public final int type;

        private final AtomicReference<ConcurrentLinkedQueue<VideoFrame>> resources = new AtomicReference<>(new ConcurrentLinkedQueue<>());
        private boolean closed = false;
        private final AtomicLong totalSize = new AtomicLong(0);
        private final AtomicLong resident = new AtomicLong(0);

        private Pool(final int h, final int w, final int type) {
            this.h = h;
            this.w = w;
            this.type = type;
        }

        public VideoFrame get(final long decodeTimeMillis, final long frameNumber) {
            final ConcurrentLinkedQueue<VideoFrame> lpool = getPool();
            if(lpool == null) // we're closed
                throw new IllegalStateException("VideoFrame Pool is shut down");
            try(QuietCloseable qc = () -> resources.set(lpool)) {
                final VideoFrame ret = lpool.poll();
                if(ret == null) {
                    totalSize.incrementAndGet();
                    return new VideoFrame(this, h, w, type, decodeTimeMillis, frameNumber);
                }
                resident.decrementAndGet();
                return ret.leavingPool(decodeTimeMillis, frameNumber);
            }
        }

        // called from VF close
        private void returnToPool(final VideoFrame vf) {
            final ConcurrentLinkedQueue<VideoFrame> lpool = getPool();
            if(lpool == null) // we're closed
                vf.reallyClose();
            else {
                try(final QuietCloseable qc = () -> resources.set(lpool);) {
                    lpool.add(vf);
                    vf.isInPool = true;
                    resident.incrementAndGet();
                }
            }
        }

        @Override
        public void close() {
            final ConcurrentLinkedQueue<VideoFrame> lpool = getPool();
            if(lpool != null) {
                closed = true;
                lpool.stream().forEach(f -> f.reallyClose());
                lpool.clear();
            } // else, if lpool is null then another thread already closed this
        }

        public long totalSize() {
            return totalSize.get();
        }

        public long numResident() {
            return resident.get();
        }

        private ConcurrentLinkedQueue<VideoFrame> getPool() {
            while(!closed) {
                final ConcurrentLinkedQueue<VideoFrame> ret = resources.getAndSet(null);
                if(ret != null)
                    return ret;
            }
            return null;
        }
    }

    public static Pool getPool(final int h, final int w, final int type) {
        return new Pool(h, w, type);
    }

    public long frameNumber() {
        return frameNumber;
    }

    @Override
    public String toString() {
        return VideoFrame.class.getSimpleName() + ": (" + getClass().getName() + "@" + Integer.toHexString(hashCode()) + ")"
            + super.toString();
    }

    @Override
    public VideoFrame returnMe() {
        // hacky, yet efficient.
        skipCloseOnceForReturn = true;
        return this;
    }

    @Override
    public void close() {
        if(skipCloseOnceForReturn) {
            skipCloseOnceForReturn = false;
            return;
        }
        if(isInPool) {
            LOGGER.warn("VideoFrame being closed twice at ", new RuntimeException());
            if(TRACK_MEMORY_LEAKS) {
                LOGGER.warn("TRACKING: originally returned to pool at:", rtpStackTrace);
                LOGGER.warn("TRACKING: create at: ", stackTrace);
            }
        } else {
            rtpStackTrace = TRACK_MEMORY_LEAKS ? new RuntimeException("VideoFrame Returned to pool here") : null;
            if(pool == null) {
                reallyClose();
            } else {
                pool.returnToPool(this);
            }
        }
    }

    private void reallyClose() {
        super.close();
    }

    public VideoFrame pooledDeepCopy(final Pool ppool) {
        final VideoFrame newMat = ppool.get(decodeTimeMillis, frameNumber);
        if(rows() != 0)
            copyTo(newMat);
        return newMat;
    }

    public VideoFrame deepCopy() {
        final VideoFrame newMat = pool == null ? new VideoFrame(decodeTimeMillis, frameNumber) : pool.get(decodeTimeMillis, frameNumber);
        if(rows() != 0)
            copyTo(newMat);
        return newMat;
    }

    public VideoFrame shallowCopy() {
        return new VideoFrame(ImageAPI.CvRaster_copy(nativeObj), decodeTimeMillis, frameNumber);
    }

    public static VideoFrame wrapNativeVideoFrame(final long nativeObj, final long decodeTimeMillis, final long frameNumber) {
        return new VideoFrame(nativeObj, decodeTimeMillis, frameNumber);
    }
}
