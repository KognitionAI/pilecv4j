package ai.kognition.pilecv4j.ipc;

import static ai.kognition.pilecv4j.ipc.ErrorHandling.EAGAIN;
import static ai.kognition.pilecv4j.ipc.ErrorHandling.throwIfNecessary;
import static net.dempsy.util.Functional.uncheck;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
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
 * There are multiple underlying implementations. Some of these implementations
 * identify a unique shared memory segment using a string (posix, windows), and some
 * using an integer (system V). This is why the constructor takes both a {@code String},
 * as well as an {@code int}. The underlying implementation may use one or the other
 * but not typically both. Therefore, when using a ShmQueue, you should make sure
 * that either both the {@code String} and the {@code int} uniquely identify the
 * shared memory segment you want OR choose the native implementation that uses
 * the one you care about.
 * </p>
 *
 * <p>
 * <b>NOTE:</b> This functionality is built on POSIX shared memory so if
 * that's not available on your platform then it wont compile there.
 * </p>
 *
 * <p>
 * <b>NOTE:</b>This class is NOT THREAD SAFE. If you want to use this across threads then
 * instantiate another one on the same shared memory segment or manage your own
 * access.
 * </p>
 *
 * <p>
 * <b>NOTE:</b>This class may be (and likely is) compiled with locking disabled
 * and so unless you know better, you should not rely on the anything that locks
 * to prevent access from the sibling process sharing data.
 * </p>
 */
public class ShmQueue implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShmQueue.class);

    public final long nativeRef;
    public final String name;

    /**
     * When passed as a timeout for acquiring a lock it means don't wait at all
     * and return immediately if the lock can't be aquired.
     */
    public static final long TRY_LOCK = 0;

    /**
     * When passed in as a timeout for acquiring a lock, it means wait forever.
     */
    public static final long INFINITE = -1;

    private long size = -1; // until it's open this is unset
    private boolean isClosed = false;

    // Oddly, because we spin on these, many get instantiated stressing the memory/gc. So we're going to reuse the
    // same one over and over. This class is not thread safe.
    private final IntByReference intResult = new IntByReference();
    private final LongByReference longResult = new LongByReference();
    private final PointerByReference ptrResult = new PointerByReference();
    private ByteBuffer reusedBb;
    private final int[] rowCol = new int[2];

    /**
     * <p>
     * Construct a ShmQueue uniquely identified by BOTH the {@code name} AND the {@code key}.
     * </p>
     *
     * <p>
     * As described on the class, there are multiple underlying implementations. Some of these
     * implementations identify a unique shared memory segment using a string (posix, windows),
     * and some using an integer (system V). This is why the constructor takes both. The underlying
     * implementation may use one or the other but not typically both. Therefore, when using a
     * ShmQueue, you should make sure that either both the {@code String} and the {@code int}
     * uniquely identify the shared memory segment you want OR choose the native implementation
     * that uses the one you care about.
     * </p>
     *
     */
    public ShmQueue(final String name, final int key) {
        this.name = name;
        nativeRef = IpcApi.pilecv4j_ipc_create_shmQueue(name, key);
    }

    /**
     * Generate the {@code int} nameRep by using the MD5 hash of the name.
     */
    public static ShmQueue createUsingMd5Hash(final String name) {
        final MessageDigest md = uncheck(() -> MessageDigest.getInstance("MD5"));
        md.update(name.getBytes());
        final int key = ByteBuffer.wrap(md.digest()).getInt();
        return new ShmQueue(name, key);
    }

    /**
     * Cleanup the native resources associated with this ShmQueue. If this is the owner
     * of the queue then the shared memory segment will also be closed.
     */
    @Override
    public void close() {
        if(isClosed)
            throw new IllegalStateException("Double close on " + this);
        IpcApi.pilecv4j_ipc_destroy_shmQueue(nativeRef);
        isClosed = true;
    }

    /**
     * Create the underlying shared memory space of the given {@code size}. If {@code owner} is
     * {@code true} then closing this ShmQueue will also close the underlying shared memory
     * segment.
     *
     * @param size is the total size in bytes of the shared memory segment.
     * @param owner is whether or not this ShmQueue is the owner and therefore will close the underlying
     *     shared memory segment when the ShmQueue is closed.
     * @param numMailboxes is how many <em>posting flags</em> to create. usually this is
     *     1 for simplex communication and 2 for duplex communication.
     */
    public void create(final long size, final boolean owner, final int numMailboxes) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_create(nativeRef, size, owner ? 1 : 0, numMailboxes), true);
        this.size = size;
        this.reusedBb = getBuffer(0);
    }

    /**
     * Create the underlying shared memory space of the given {@code size}. If {@code owner} is
     * {@code true} then closing this ShmQueue will also close the underlying shared memory
     * segment. This is equivalent to calling {@code create(size, owner, 1)}
     *
     * @param size is the total size in bytes of the shared memory segment.
     * @param owner is whether or not this ShmQueue is the owner and therefore will close the underlying
     *     shared memory segment when the ShmQueue is closed.
     */
    public void create(final long size, final boolean owner) {
        create(size, owner, 1);
    }

    /**
     * Open an existing shared memory segment from this process. If {@code owner} is
     * {@code true} then closing this ShmQueue will also close the underlying shared memory
     * segment.
     *
     * @param owner is whether or not this ShmQueue is the owner and therefore will close the underlying
     *     shared memory segment when the ShmQueue is closed.
     * @return true if the segment is opened. If the named shared memory segment doesn't exist
     * then return false.
     */
    public boolean open(final boolean owner) {
        final long result = throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_open(nativeRef, owner ? 1 : 0), false);
        if(result == EAGAIN)
            return false;

        size = getSize();
        this.reusedBb = getBuffer(0);
        return true;
    }

    /**
     * This will reset and clear the underlying shared memory segment and set all mailboxes
     * to empty.
     */
    public void reset() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_reset(nativeRef), true);
    }

    /**
     * Is the shared memory segment currently opened by this {@link ShmQueue}
     */
    public boolean isOpen() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_isOpen(nativeRef, intResult), true);
        return intResult.getValue() == 0 ? false : true;
    }

    /**
     * Is the shared memory segment currently owned by this {@link ShmQueue}
     */
    public boolean isOwner() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_isOwner(nativeRef, intResult), true);
        return intResult.getValue() == 0 ? false : true;
    }

    /**
     * This will delete the shared memory segment. Normally this is done automatically when closed
     * but it can be done explicitly. The shared memory segment will still be usable until it's
     * closed. However, it will not be discoverable from another process and so can't be re-{@code open}ed.
     * If another process @code create}s one with the same name, it will not be this one.
     */
    public void unlink() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unlink(nativeRef), true);
    }

    /**
     * Obtain access to the shared memory segment locking it if possible (the
     * native code would have needed to be compiled with the -DLOCKING enabled). The
     * method will wait up to {@code timeoutMillis} milliseconds to obtain the lock (if
     * locking is enabled in the native code) and will return false if it cannot. If it
     * gets the lock (or if the native code is compiled without locking) then it will
     * pass a ByteBuffer to the lambda that can then write or read the segment.
     *
     * @param bbconsumer is the lambda that will be passed a ByteBuffer with access to the
     *     shared memory if access is obtained.
     * @param timeoutMillis is the time to wait in milliseconds to get the lock (if the native
     *     code is actually compiled for locking).
     * @return true if the access was obtained and the lambda was passed the ByteBuffer access
     * to the shared memory segment.
     */
    public boolean access(final Consumer<ByteBuffer> bbconsumer, final long timeoutMillis) {
        final boolean gotLock = lock(timeoutMillis);
        if(gotLock) {
            try(QuietCloseable qc = () -> unlock();) {
                reusedBb.rewind();
                bbconsumer.accept(reusedBb);
                return true;
            }
        } else
            return false;
    }

    /**
     * Obtain access to the shared memory segment locking it if possible (the
     * native code would have needed to be compiled with the -DLOCKING enabled). The
     * method will wait forever to obtain the lock (if
     * locking is enabled in the native code) and will return false if it cannot. If it
     * gets the lock (or if the native code is compiled without locking) then it will
     * pass a ByteBuffer to the lambda that can then write or read the segment. This
     * is equivalent to calling {@code access(bbconsumer, INFINITE)}
     *
     * @param bbconsumer is the lambda that will be passed a ByteBuffer with access to the
     *     shared memory if access is obtained.
     * @return true if the access was obtained and the lambda was passed the ByteBuffer access
     * to the shared memory segment.
     */
    public boolean access(final Consumer<ByteBuffer> bbconsumer) {
        return access(bbconsumer, INFINITE);
    }

    /**
     * Obtain access to the shared memory segment locking it if possible (the
     * native code would have needed to be compiled with the -DLOCKING enabled). The
     * method try to obtain the lock once without waiting (if
     * locking is enabled in the native code) and will return false if it cannot. If it
     * gets the lock (or if the native code is compiled without locking) then it will
     * pass a ByteBuffer to the lambda that can then write or read the segment. This
     * is equivalent to calling {@code access(bbconsumer, TRY_LOCK)}
     *
     * @param bbconsumer is the lambda that will be passed a ByteBuffer with access to the
     *     shared memory if access is obtained.
     * @return true if the access was obtained and the lambda was passed the ByteBuffer access
     * to the shared memory segment.
     */
    public boolean tryAccess(final Consumer<ByteBuffer> bbconsumer) {
        return access(bbconsumer, TRY_LOCK);
    }

    /**
     * Mark the data in the shared memory to be read by another process. This
     * is equivalent to calling {@code post(0)} and assumes simplex communication.
     */
    public void post() {
        post(0);
    }

    /**
     * Mark a particular mailbox as ready for the data in the shared memory to be read
     * by another process
     */
    public void post(final int mailbox) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_postMessage(nativeRef, mailbox), true);
    }

    /**
     * Mark the data in the shared memory as having been read and so another process can write
     * the next message. This is equivalent to calling {@code post(0)} and assumes simplex communication.
     */
    public void unpost() {
        unpost(0);
    }

    /**
     * Mark the data in the shared memory as having been read through the given mailbox and so another process can write
     * the next message.
     */
    public void unpost(final int mailbox) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unpostMessage(nativeRef, mailbox), true);
    }

    /**
     * A {@code CvMat} that represents a shared memory segment or a portion of a shared memory segment
     * that automatically manages the underlying locking mechanism (if the native code is compiled
     * with -DLOCKING). If locking is enabled, the lock will be held as long as this mat hasn't bee
     * closed.
     */
    public class ShmQueueCvMat extends CvMat {
        boolean gotLock = false;

        private ShmQueueCvMat(final long nativeRef) {
            super(nativeRef);
        }

        @Override
        protected void doNativeDelete() {
            if(gotLock)
                unlock();
        }

        /**
         * Mark a particular mailbox as ready for the data in the shared memory to be read
         * by another process. This is a convenience method for {@code ShmQueue.this.post(mailbox)}
         */
        public void post(final int mailbox) {
            ShmQueue.this.post(mailbox);
        }

        /**
         * Mark the data in the shared memory to be read by another process. This
         * is equivalent to calling {@code post(0)} and assumes simplex communication.
         */
        public void post() {
            post(0);
        }

        /**
         * Mark the data in the shared memory as having been read through the given mailbox and so another process can write
         * the next message. This is a convenience method for {@code ShmQueue.this.unpost(mailbox)}
         */
        public void unpost(final int mailbox) {
            ShmQueue.this.unpost(mailbox);
        }

        /**
         * Mark the data in the shared memory as having been read and so another process can write
         * the next message. This is equivalent to calling {@code post(0)} and assumes simplex communication.
         */
        public void unpost() {
            unpost(0);
        }
    }

    /**
     * This checks to see if a message has been posted to the given mailbox.
     */
    public boolean isMessageAvailable(final int mailbox) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_isMessageAvailable(nativeRef, intResult, mailbox), true);
        return intResult.getValue() == 0 ? false : true;
    }

    /**
     * In simplex mode, when there's only one mailbox, this checks to see if a message has been
     * posted to the mailbox. It's equivalent to {@code isMessageAvailable(0)}
     */
    public boolean isMessageAvailable() {
        return isMessageAvailable(0);
    }

    /**
     * This checks to see if there's room to post a message to the given mailbox.
     */
    public boolean canWriteMessage(final int mailbox) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_canWriteMessage(nativeRef, intResult, mailbox), true);
        return intResult.getValue() == 0 ? false : true;
    }

    /**
     * In simplex mode, when there's only one mailbox, this checks to see if there's
     * room to post a message to the mailbox. It's equivalent to {@code canWriteMessage(0)}
     */
    public boolean canWriteMessage() {
        return canWriteMessage(0);
    }

    /**
     * Present the shared wrapped in a Mat. If locking is enabled then lock will be acquired
     * if possible and held until the mat is closed. If the lock cannot be acquired in the
     * time given then null will be returned.
     *
     * @param offset is the offset into the shared memory segment where the mat data begins
     * @param sizes is the dimensions of the mat.
     * @param type is the CvType of the data in the mat.
     * @param millis is the milliseconds to wait to acquire the lock. This can be {@link #INFINITE}
     *     to wait forever or {@link #TRY_LOCK} to make one attempt and return immediately.
     * @return if the lock can be obtained, a Mat representing the shared memory segment or part
     * thereof. When the Mat is closed the lock will be released. If the lock cannot be obtained,
     * {@code null} will be returned.
     */
    public ShmQueueCvMat accessAsMat(final long offset, final int[] sizes, final int type, final long millis) {
        try(CvMat ret = getUnlockedBufferAsMat(offset, sizes, type);
            ShmQueueCvMat aret = shallowCopy(ret);) {
            aret.gotLock = lock(millis);
            if(aret.gotLock)
                return (ShmQueueCvMat)aret.returnMe();
            else
                return null;
        }
    }

    /**
     * Convenience method for access as a 2D Mat. It's the same as calling
     *
     * <code>
     * <pre>
     * accessAsMat(offset, new int[] {rows,cols}, type, millis)
     * </pre>
     * </code>
     *
     * @see #accessAsMat(long, int[], int, long)
     */
    public ShmQueueCvMat accessAsMat(final long offset, final int rows, final int cols, final int type, final long millis) {
        rowCol[0] = rows;
        rowCol[1] = cols;

        return accessAsMat(offset, rowCol, type, millis);
    }

    /**
     * Convenience method for access as a 2D Mat. It's the same as calling
     *
     * <code>
     * <pre>
     * accessAsMat(offset, new int[] {rows,cols}, type, INFINITE)
     * </pre>
     * </code>
     *
     * @see #accessAsMat(long, int[], int, long)
     */
    public ShmQueueCvMat accessAsMat(final long offset, final int rows, final int cols, final int type) {
        return accessAsMat(offset, rows, cols, type, INFINITE);
    }

    /**
     * Convenience method. It's the same as calling
     *
     * <code>
     * <pre>
     * accessAsMat(offset, sizes, type, INFINITE)
     * </pre>
     * </code>
     *
     * @see #accessAsMat(long, int[], int, long)
     */
    public ShmQueueCvMat accessAsMat(final long offset, final int[] sizes, final int type) {
        return accessAsMat(offset, sizes, type, INFINITE);
    }

    /**
     * Convenience method for access as a 2D Mat. It's the same as calling
     *
     * <code>
     * <pre>
     * accessAsMat(offset, new int[] {rows,cols}, type, TRY_LOCK)
     * </pre>
     * </code>
     *
     * @see #accessAsMat(long, int[], int, long)
     */
    public ShmQueueCvMat tryAccessAsMat(final long offset, final int rows, final int cols, final int type) {
        return accessAsMat(offset, rows, cols, type, TRY_LOCK);
    }

    /**
     * Convenience method. It's the same as calling
     *
     * <code>
     * <pre>
     * accessAsMat(offset, sizes, type, TRY_LOCK)
     * </pre>
     * </code>
     *
     * @see #accessAsMat(long, int[], int, long)
     */
    public ShmQueueCvMat tryAccessAsMat(final long offset, final int[] sizes, final int type) {
        return accessAsMat(offset, sizes, type, TRY_LOCK);
    }

    /**
     * Returns a pointer to the native location within the shared memory segment.
     */
    public long getRawBuffer(final long offset) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, offset, ptrResult), true);
        return Pointer.nativeValue(ptrResult.getValue());
    }

    /**
     * This will return a ByteBuffer representing the entire shared memory segment. Each
     * call will return the IDENTICAL byte buffer.
     *
     */
    public ByteBuffer getReusedByteBuffer() {
        return reusedBb;
    }

    /**
     * Return a ByteBuffer mapping the portion of the shared memory segment requested.
     *
     * @param offset is the offset in bytes into the shared memory segment where the resulting
     *     ByteBuffer should begin.
     */
    public ByteBuffer getBuffer(final long offset) {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, offset, ptrResult), true);
        final Pointer data = ptrResult.getValue();
        if(Pointer.nativeValue(data) == 0)
            throw new IpcException("Null data buffer");
        return data.getByteBuffer(0, size - offset);
    }

    /**
     * Return a ByteBuffer mapping the portion of the shared memory segment requested.
     *
     * @param offset is the offset in bytes into the shared memory segment where the resulting
     *     ByteBuffer should begin.
     */
    public ByteBuffer getBuffer(final long offset, final long length) {
        if(length + offset > size) {
            LOGGER.error("Cannot allocate a bytebuffer of size {} with offset {} when the underlying data is only {} bytes long", length, offset, size);
            throw new BufferOverflowException();
        }
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_buffer(nativeRef, offset, ptrResult), true);
        final Pointer data = ptrResult.getValue();
        if(Pointer.nativeValue(data) == 0)
            throw new IpcException("Null data buffer");
        return data.getByteBuffer(0, length);
    }

    /**
     * Obtain the lock if possible. If the native code isn't compiled with locking enabled
     * then this method will always return {@code true}.
     *
     * @param millis is the maximum amount of time to wait in milliseconds to obtain the lock.
     * @return {@code true} of the lock was obtained. {@code false} otherwise.
     */
    public boolean lock(final long millis) {
        return (throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_lock(nativeRef, millis, 0), false) == EAGAIN) ? false : true;
    }

    /**
     * <p>
     * Obtain the lock waiting forever if necessary. If the native code isn't compiled with locking enabled
     * then this method will always return {@code true}.
     * </p>
     *
     * <p>
     * This is a convenience method and is the same as calling {@code lock(INFINITE)}.
     * </p>
     */
    public boolean lock() {
        return lock(INFINITE);
    }

    /**
     * <p>
     * Obtain the lock if it's immediately available. If the native code isn't compiled with locking enabled
     * then this method will always return {@code true}.
     * </p>
     *
     * <p>
     * This is a convenience method and is the same as calling {@code lock(TRY_LOCK)}.
     * </p>
     */
    public boolean tryLock() {
        return lock(TRY_LOCK);
    }

    /**
     * Return a Resource that can be auto-closed. It will return null if the lock cannot be accessed
     * so the return value will need to be checked.
     */
    public QuietCloseable lockAsResource(final long timeoutMillis) {
        return lock(timeoutMillis) ? () -> unlock() : null;
    }

    /**
     * Return a Resource that can be auto-closed. It will return null if the lock cannot be accessed
     * so the return value will need to be checked. Convenience method for {@code lockAsResource(TRY_LOCK)}
     */
    public QuietCloseable tryLockAsResource() {
        return lockAsResource(TRY_LOCK);
    }

    /**
     * Return a Resource that can be auto-closed. It will return null if the lock cannot be accessed
     * so the return value will need to be checked. Convenience method for {@code lockAsResource(INFINITE)}
     */
    public QuietCloseable lockAsResource() {
        return lockAsResource(INFINITE);
    }

    /**
     * If you're holding the lock, then release it. If the native code isn't compiled with locking enabled,
     * this method will do nothing. If you're not holding the lock, this method will likely put the lock
     * management in an unmanageable state.
     */
    public void unlock() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_unlock(nativeRef), true);
    }

    /**
     * This will return the size of the shared memory segment. The shared memory segment
     * must be open already.
     */
    public long getSize() {
        throwIfNecessary(IpcApi.pilecv4j_ipc_shmQueue_bufferSize(nativeRef, longResult), true);
        return longResult.getValue();
    }

    @Override
    public String toString() {
        return "ShmQueue [nativeRef=" + nativeRef + ", name=" + name + ", size=" + size + "]";
    }

    /**
     * This will return {@code true} if the native code has been compiled to enabled
     * locking. Otherwise it will return {@code false}.
     */
    public static boolean isLockingEnabled() {
        return IpcApi.pilecv4j_ipc_locking_isLockingEnabled() == 1 ? true : false;
    }

    public CvMat getUnlockedBufferAsMat(final long offset, final int[] sizes, final int type) {
        if(sizes == null || sizes.length == 0)
            return new CvMat();
        final long nativeData = getRawBuffer(offset); // this will throw an exeception if it's not open so we wont
        // need to worry about 'size' being set.
        long matSizeBytes = CvType.ELEM_SIZE(type);
        for(final int sz: sizes)
            matSizeBytes *= sz;
        if(matSizeBytes > size)
            throw new IpcException("Can't allocate a mat with " + matSizeBytes + " bytes given a data buffer of " + size + " bytes");
        try(CvMat ret = CvMat.create(sizes, type, nativeData);) {
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
