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

/**
 * <p>
 * The class can be used to do IPC through shared memory.
 * </p>
 *
 * <p>
 * It's specifically optimized for a single writer and a single reader
 * and so shouldn't be used anything other than one-to-one configuration though
 * it can run <em>duplex</em> for request-response cases.
 * </p>
 *
 * <p>
 * <b>NOTE:</b> This functionality is built on POSIX shared memory so if
 * that's not available on your platform then it wont compile there.
 * </p>
 *
 * <p>
 * <b>NOTE:</b>This class is NOT THREAD SAFE. If you want to use this across threads then
 * instantiate another one on the same shared memory segment.
 * </p>
 *
 */
public class ShmQueue implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShmQueue.class);

    public final long nativeRef;

    public static final long TRY_LOCK = 0;
    public static final long INFINITE = -1;

    private long size = -1; // until it's open this is unset

    // Oddly, because we spin on these, many get instantiated stressing the memory/gc. So we're going to reuse the
    // same one over and over. This class is not thread safe.
    private final IntByReference intResult = new IntByReference();
    private final LongByReference longResult = new LongByReference();
    private final PointerByReference ptrResult = new PointerByReference();

    public ShmQueue(final String name) {
        nativeRef = IpcApi.pilecv4j_ipc_create_shmQueue(name);
    }

    @Override
    public void close() {
        IpcApi.pilecv4j_ipc_destroy_shmQueue(nativeRef);
    }

    public void create(final long size, final boolean owner, final int numMailboxes) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_create(nativeRef, size, owner ? 1 : 0, numMailboxes), true);
        this.size = size;
    }

    public void create(final long size, final boolean owner) {
        create(size, owner, 1);
    }

    public void create(final Mat mat, final boolean owner, final int numMailboxes) {
        create(mat.total() * mat.elemSize(), owner, numMailboxes);
    }

    public void create(final Mat mat, final boolean owner) {
        create(mat, owner, 1);
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

        public void post(final int mailbox) {
            throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_postMessage(nativeRef, mailbox), true);
        }

        public void post() {
            post(0);
        }

        public void unpost(final int mailbox) {
            throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unpostMessage(nativeRef, mailbox), true);
        }

        public void unpost() {
            unpost(0);
        }

    }

    public boolean isMessageAvailable(final int mailbox) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_isMessageAvailable(nativeRef, intResult, mailbox), true);
        return intResult.getValue() == 0 ? false : true;
    }

    public boolean isMessageAvailable() {
        return isMessageAvailable(0);
    }

    public boolean canWriteMessage(final int mailbox) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_canWriteMessage(nativeRef, intResult, mailbox), true);
        return intResult.getValue() == 0 ? false : true;
    }

    public boolean canWriteMessage() {
        return canWriteMessage(0);
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

    public long getRawBuffer() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, 0, ptrResult), true);
        return Pointer.nativeValue(ptrResult.getValue());
    }

    public ByteBuffer getBuffer() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, 0, ptrResult), true);
        final Pointer data = ptrResult.getValue();
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

    public boolean tryLock() {
        return lock(TRY_LOCK);
    }

    public void unlock() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unlock(nativeRef), true);
    }

    public long getBufferSize() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_bufferSize(nativeRef, longResult), true);
        return longResult.getValue();
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
