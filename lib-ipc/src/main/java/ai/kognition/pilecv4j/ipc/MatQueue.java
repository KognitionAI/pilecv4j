package ai.kognition.pilecv4j.ipc;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.ipc.internal.IpcApi;

public class MatQueue implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatQueue.class);

    public final long nativeRef;

    public class ShmWriteMat extends CvMat {
        private ShmWriteMat(final long nativeRef) {
            super(nativeRef);
        }

        @Override
        public void close() {
            super.close();
            if(IpcApi.pilecv4j_ipc_shmQueue_markWritten(nativeRef) == 0)
                LOGGER.warn("Couldn't mark shm queue as written.");
        }
    }

    public class ShmReadMat extends CvMat {
        private ShmReadMat(final long nativeRef) {
            super(nativeRef);
        }

        @Override
        public void close() {
            super.close();
            if(IpcApi.pilecv4j_ipc_shmQueue_markRead(nativeRef) == 0)
                LOGGER.warn("Couldn't mark shm queue as read.");
        }
    }

    public MatQueue(final String name) {
        nativeRef = IpcApi.pilecv4j_ipc_create_shmQueue(name);
    }

    @Override
    public void close() {
        IpcApi.pilecv4j_ipc_destroy_shmQueue(nativeRef);
    }

    public boolean create(final long size, final boolean owner) {
        return IpcApi.pilecv4j_ipc_shmQueue_create(nativeRef, size, owner ? 1 : 0) == 0 ? false : true;
    }

    public boolean create(final Mat mat, final boolean owner) {
        return create(mat.total() * mat.elemSize(), owner);
    }

    public boolean open(final boolean owner) {
        return IpcApi.pilecv4j_ipc_shmQueue_open(nativeRef, owner ? 1 : 0) == 0 ? false : true;
    }

    public CvMat tryGetReadView(final long numElements, final int cvtype) {
        final long ref = IpcApi.pilecv4j_ipc_shmQueue_tryGetReadView_asMat(nativeRef, numElements, cvtype);
        if(ref == 0L)
            return null;
        return new ShmReadMat(ref);
    }

    public CvMat getReadView(final long numElements, final int cvtype) {
        CvMat ret = null;
        while(ret == null) {
            try(CvMat tmp = tryGetReadView(numElements, cvtype);) {
                if(tmp != null)
                    ret = tmp.returnMe();
                else
                    Thread.yield();
            }
        }
        return ret;
    }

    public CvMat copyReadView(final long numElements, final int cvtype) {
        try(CvMat res = getReadView(numElements, cvtype);) {
            if(res != null) {
                try(CvMat ret = new CvMat();) {
                    res.copyTo(ret);
                    return ret.returnMe();
                }
            }
            return null;
        }
    }

    public CvMat tryGetWriteView(final long numElements, final int cvtype) {
        final long ref = IpcApi.pilecv4j_ipc_shmQueue_tryGetWriteView_asMat(nativeRef, numElements, cvtype);
        if(ref == 0L)
            return null;
        return new ShmWriteMat(ref);
    }

    public CvMat getWriteView(final long numElements, final int cvtype) {
        CvMat ret = null;
        while(ret == null) {
            try(CvMat tmp = tryGetWriteView(numElements, cvtype);) {
                if(tmp != null)
                    ret = tmp.returnMe();
                else
                    Thread.yield();
            }
        }
        return ret;
    }

    public boolean copyWriteView(final CvMat toWrite) {
        try(CvMat res = getWriteView(toWrite.total(), toWrite.type());) {
            if(res != null) {
                toWrite.copyTo(res);
                return true;
            }
            return false;
        }
    }

}
