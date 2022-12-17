package ai.kognition.pilecv4j.ipc;

import static ai.kognition.pilecv4j.ipc.ErrorHandling.EAGAIN;
import static ai.kognition.pilecv4j.ipc.ErrorHandling.throwIfNecessary;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;
import ai.kognition.pilecv4j.ipc.internal.IpcApi;

public class ShmQueue implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShmQueue.class);

    public final long nativeRef;

    public static final long TRY_LOCK = 0;
    public static final long INFINITE = -1;

    private long size = -1; // until it's open this is unset

    public ShmQueue(final String name) {
        nativeRef = IpcApi.pilecv4j_ipc_create_shmQueue(name);
    }

    @Override
    public void close() {
        IpcApi.pilecv4j_ipc_destroy_shmQueue(nativeRef);
    }

    public void create(final long size, final boolean owner) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_create(nativeRef, size, owner ? 1 : 0), true);
        this.size = size;
    }

    public void create(final Mat mat, final boolean owner) {
        create(mat.total() * mat.elemSize(), owner);
    }

    public boolean open(final boolean owner) {
        final long result = throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_open(nativeRef, owner ? 1 : 0), false);
        if(result == EAGAIN)
            return false;

        size = getBufferSize();
        return true;
    }

    public boolean access(final Consumer<ByteBuffer> bbconsumer, final long millis) {
        final boolean gotLock = lock(millis);
        if(gotLock) {
            try(QuietCloseable qc = () -> unlock();) {
                bbconsumer.accept(getBuffer());
                return true;
            }
        } else
            return false;
    }

    public boolean access(final Consumer<ByteBuffer> bbconsumer) {
        return access(bbconsumer, INFINITE);
    }

    public boolean tryAccess(final Consumer<ByteBuffer> bbconsumer) {
        return access(bbconsumer, TRY_LOCK);
    }

    public class ShmQueueCvMat extends CvMat {
        boolean gotLock = false;

        private ShmQueueCvMat(final long nativeRef) {
            super(nativeRef);
        }

        @Override
        public void close() {
            try {
                if(!skipCloseOnceForReturn) {
                    if(gotLock)
                        unlock();
                }
            } finally {
                // if skipCloseOnceForReturn is set, this will bookkeep it correctly.
                super.close();
            }
        }

        public void post() {
            throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_postMessage(nativeRef), true);
        }

        public void unpost() {
            throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unpostMessage(nativeRef), true);
        }

    }

    public ShmQueueCvMat accessAsMat(final int rows, final int cols, final int type, final long millis) {
        try(CvMat ret = getUnlockedBufferAsMat(rows, cols, type);
            ShmQueueCvMat aret = shallowCopy(ret);) {
            aret.gotLock = lock(millis);
            if(aret.gotLock)
                return (ShmQueueCvMat)aret.returnMe();
            else
                return null;
        }
    }

    public ShmQueueCvMat accessAsMat(final int rows, final int cols, final int type) {
        return accessAsMat(rows, cols, type, INFINITE);
    }

    public ShmQueueCvMat tryAccessAsMat(final int rows, final int cols, final int type) {
        return accessAsMat(rows, cols, type, TRY_LOCK);
    }

    public boolean isMessageAvailable() {
        final IntByReference result = new IntByReference();
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_isMessageAvailable(nativeRef, result), true);
        return result.getValue() == 0 ? false : true;
    }

    public long getRawBuffer() {
        final var ret = new PointerByReference();
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, 0, ret), true);
        return Pointer.nativeValue(ret.getValue());
    }

    public ByteBuffer getBuffer() {
        final var buf = new PointerByReference();
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, 0, buf), true);
        final Pointer data = buf.getValue();
        if(Pointer.nativeValue(data) == 0)
            throw new IpcException("Null data buffer");
        return data.getByteBuffer(0, size);
    }

    public boolean lock(final long millis) {
        return (throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_lock(nativeRef, millis, 0), false) == EAGAIN) ? false : true;
    }

    public boolean lock() {
        return lock(INFINITE);
    }

    public boolean tryLockWrite() {
        return lock(TRY_LOCK);
    }

    public void unlock() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unlock(nativeRef), true);
    }

    public long getBufferSize() {
        final var ret = new LongByReference();
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_bufferSize(nativeRef, ret), true);
        return ret.getValue();
    }

    private CvMat getUnlockedBufferAsMat(final int rows, final int cols, final int type) {
        final long nativeData = getRawBuffer(); // this will throw an exeception if it's not open so we wont
                                                // need to worry about 'size' being set.
        final long matSizeBytes = (long)CvType.ELEM_SIZE(type) * rows * cols;
        if(matSizeBytes > size)
            throw new IpcException("Can't allocate a mat with " + matSizeBytes + " bytes given a data buffer of " + size + " bytes");
        try(CvMat ret = CvMat.create(rows, cols, type, nativeData);) {
            return ret.returnMe();
        }
    }

    private ShmQueueCvMat shallowCopy(final Mat mat) {
        final long newNativeObj = ImageAPI.pilecv4j_image_CvRaster_copy(mat.nativeObj);
        if(newNativeObj == 0L) {
            // let's do some checking
            if(!mat.isContinuous())
                LOGGER.error("Cannot shallow copy a discontinuous Mat");
            else
                LOGGER.error("Failed to shallow copy mat");
            return null;
        }
        return new ShmQueueCvMat(newNativeObj);
    }

}
