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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

public class VideoFrame extends CvMat {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoFrame.class);

    public long decodeTimeMillis;

    private final Pool pool;
    private boolean isInPool = false;
    private RuntimeException rtpStackTrace = null;
    private boolean skipCloseOnceForReturn = false;

    private long frameNumber;
    public final boolean isRgb;

    public VideoFrame(final long nativeObj, final long decodeTimeMillis, final long frameNumber, final boolean isRgb) {
        super(nativeObj);
        this.pool = null;
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
        this.isRgb = isRgb;
    }

    public VideoFrame(final long decodeTimeMillis, final long frameNumber, final boolean isRgb) {
        super();
        this.pool = null;
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
        this.isRgb = isRgb;
    }

    private VideoFrame(final Pool pool, final int h, final int w, final int type, final long decodeTimeMillis, final long frameNumber, final boolean isRgb) {
        super(h, w, type);
        this.pool = pool;
        this.decodeTimeMillis = decodeTimeMillis;
        this.frameNumber = frameNumber;
        this.isRgb = isRgb;
    }

    public static VideoFrame create(final int rows, final int cols, final int type, final long pointer, final long decodeTimeMillis, final long frameNumber,
        final boolean isRgb) {
        final long nativeObj = ImageAPI.pilecv4j_image_CvRaster_makeMatFromRawDataReference(rows, cols, type, pointer);
        if(nativeObj == 0)
            throw new NullPointerException("Cannot create a CvMat from a null pointer data buffer.");
        return VideoFrame.wrapNativeVideoFrame(nativeObj, decodeTimeMillis, frameNumber, isRgb);
    }

    public VideoFrame rgb(final boolean garanteeDeepCopy) {
        if(!isRgb) {
            if(LOGGER.isTraceEnabled())
                LOGGER.trace("Converting {} from BGR to RGB. {}", VideoFrame.class.getSimpleName(), toString());

            return swapBgrRgb();
        }
        if(LOGGER.isTraceEnabled())
            LOGGER.trace("Returning {} in RGB as-is. {}", VideoFrame.class.getSimpleName(), toString());

        return garanteeDeepCopy ? this.deepCopy() : this.shallowCopy();
    }

    public VideoFrame bgr(final boolean garanteeDeepCopy) {
        if(isRgb) {
            if(LOGGER.isTraceEnabled())
                LOGGER.trace("Converting {} from RGB to BGR. {}", VideoFrame.class.getSimpleName(), toString());

            return swapBgrRgb();
        }
        if(LOGGER.isTraceEnabled())
            LOGGER.trace("Returning {} in BGR as-is. {}", VideoFrame.class.getSimpleName(), toString());

        return garanteeDeepCopy ? this.deepCopy() : this.shallowCopy();
    }

    private VideoFrame swapBgrRgb() {
        if(channels() != 3) {
            throw new IllegalArgumentException("Can only convert a 3 channel image from RGB to BGR or vice versa.");
        }

        try(final VideoFrame swapped = new VideoFrame(decodeTimeMillis, frameNumber, !isRgb)) {
            Imgproc.cvtColor(this, swapped, isRgb ? Imgproc.COLOR_RGB2BGR : Imgproc.COLOR_BGR2RGB);
            return swapped.returnMe();
        }
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
        public final boolean isRgb;
        public final int type;

        private final AtomicReference<ConcurrentLinkedQueue<VideoFrame>> resources = new AtomicReference<>(new ConcurrentLinkedQueue<>());
        private boolean closed = false;
        private final AtomicLong totalSize = new AtomicLong(0);
        private final AtomicLong resident = new AtomicLong(0);

        private Pool(final int h, final int w, final int type, final boolean isRgb) {
            this.h = h;
            this.w = w;
            this.type = type;
            this.isRgb = isRgb;
        }

        public VideoFrame get(final long decodeTimeMillis, final long frameNumber) {
            final ConcurrentLinkedQueue<VideoFrame> lpool = getPool();
            if(lpool == null) // we're closed
                throw new IllegalStateException("VideoFrame Pool is shut down");
            try(QuietCloseable qc = () -> resources.set(lpool)) {
                final VideoFrame ret = lpool.poll();
                if(ret == null) {
                    totalSize.incrementAndGet();
                    return new VideoFrame(this, h, w, type, decodeTimeMillis, frameNumber, isRgb);
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

    public static Pool getPool(final int h, final int w, final int type, final boolean isRgb) {
        return new Pool(h, w, type, isRgb);
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
        final VideoFrame newMat = pool == null ? new VideoFrame(decodeTimeMillis, frameNumber, isRgb) : pool.get(decodeTimeMillis, frameNumber);
        if(rows() != 0)
            copyTo(newMat);
        return newMat;
    }

    public VideoFrame shallowCopy() {
        return new VideoFrame(ImageAPI.pilecv4j_image_CvRaster_copy(nativeObj), decodeTimeMillis, frameNumber, isRgb);
    }

    public static VideoFrame wrapNativeVideoFrame(final long nativeObj, final long decodeTimeMillis, final long frameNumber, final boolean isRgb) {
        return new VideoFrame(nativeObj, decodeTimeMillis, frameNumber, isRgb);
    }
}
