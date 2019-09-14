package ai.kognition.pilecv4j.gstreamer;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;

public class VideoFrame extends CvMat {

    public long pts;
    public long dts;
    public long duration;
    public long decodeTimeMillis;

    private final Pool pool;

    protected VideoFrame(final long nativeObj, final long pts, final long dts, final long duration, final long decodeTimeMillis) {
        super(nativeObj);
        this.pts = pts;
        this.dts = dts;
        this.duration = duration;
        this.pool = null;
        this.decodeTimeMillis = decodeTimeMillis;
    }

    public VideoFrame(final long pts, final long dts, final long duration, final long decodeTimeMillis) {
        super();
        this.pts = pts;
        this.dts = dts;
        this.duration = duration;
        this.pool = null;
        this.decodeTimeMillis = decodeTimeMillis;
    }

    public VideoFrame(final int h, final int w, final int type, final long pts, final long dts, final long duration, final long decodeTimeMillis) {
        this(null, h, w, type, pts, dts, duration, decodeTimeMillis);
    }

    private VideoFrame(final Pool pool, final int h, final int w, final int type, final long pts, final long dts, final long duration,
        final long decodeTimeMillis) {
        super(h, w, type);
        this.pts = pts;
        this.dts = dts;
        this.duration = duration;
        this.pool = pool;
        this.decodeTimeMillis = decodeTimeMillis;
    }

    public static VideoFrame create(final int rows, final int cols, final int type, final long pointer, final long pts, final long dts, final long duration,
        final long decodeTimeMillis) {
        final long nativeObj = ImageAPI.CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
        if(nativeObj == 0)
            throw new NullPointerException("Cannot create a CvMat from a null pointer data buffer.");
        return VideoFrame.wrapNativeVideoFrame(nativeObj, pts, dts, duration, decodeTimeMillis);
    }

    public VideoFrame setTiming(final long pts, final long dts, final long duration, final long decodeTimeMillis) {
        this.pts = pts;
        this.dts = dts;
        this.duration = duration;
        this.decodeTimeMillis = decodeTimeMillis;
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

        public VideoFrame get(final long pts, final long dts, final long duration, final long decodeTimeMillis) {
            final ConcurrentLinkedQueue<VideoFrame> lpool = getPool();
            if(lpool == null) // we're closed
                throw new IllegalStateException("VideoFrame Pool is shut down");
            try (QuietCloseable qc = () -> resources.set(lpool)) {
                final VideoFrame ret = lpool.poll();
                if(ret == null) {
                    totalSize.incrementAndGet();
                    return new VideoFrame(this, h, w, type, pts, dts, duration, decodeTimeMillis);
                } else
                    resident.decrementAndGet();
                return ret;
            }
        }

        public void returnToPool(final VideoFrame vf) {
            final ConcurrentLinkedQueue<VideoFrame> lpool = getPool();
            if(lpool == null) // we're closed
                vf.reallyClose();
            else {
                try (final QuietCloseable qc = () -> resources.set(lpool);) {
                    lpool.add(vf);
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

    @Override
    public String toString() {
        return VideoFrame.class.getSimpleName() + ": (" + getClass().getName() + "@" + Integer.toHexString(hashCode()) + ")"
            + ", pts: " + pts
            + ", dts: " + dts
            + ", duration: " + duration
            + super.toString();
    }

    @Override
    public void close() {
        if(pool == null) {
            reallyClose();
        } else {
            pool.returnToPool(this);
        }
    }

    private void reallyClose() {
        super.close();
    }

    public VideoFrame pooledDeepCopy(final Pool ppool) {
        final VideoFrame newMat = ppool.get(pts, dts, duration, decodeTimeMillis);
        if(rows() != 0)
            copyTo(newMat);
        return newMat;
    }

    public VideoFrame deepCopy() {
        final VideoFrame newMat = pool == null ? new VideoFrame(pts, dts, duration, decodeTimeMillis) : pool.get(pts, dts, duration, decodeTimeMillis);
        if(rows() != 0)
            copyTo(newMat);
        return newMat;
    }

    public VideoFrame shallowCopy() {
        return new VideoFrame(ImageAPI.CvRaster_copy(nativeObj), pts, dts, duration);
    }

    private static VideoFrame wrapNativeVideoFrame(final long nativeObj, final long pts, final long dts, final long duration, final long decodeTimeMillis) {
        return new VideoFrame(nativeObj, pts, dts, duration, decodeTimeMillis);
    }
}
