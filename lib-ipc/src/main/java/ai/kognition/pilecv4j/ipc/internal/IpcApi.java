package ai.kognition.pilecv4j.ipc.internal;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.util.NativeLibraryLoader;

public class IpcApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(IpcApi.class);

    public static final String LIBNAME = "ai.kognition.pilecv4j.ipc";

    // needs to match LogLevel enum in the C++ code.
    public static final int LOG_LEVEL_TRACE = 0;
    public static final int LOG_LEVEL_DEBUG = 1;
    public static final int LOG_LEVEL_INFO = 2;
    public static final int LOG_LEVEL_WARN = 3;
    public static final int LOG_LEVEL_ERROR = 4;
    public static final int LOG_LEVEL_FATAL = 5;

    public static void _init() {}

    static {
        NativeLibraryLoader.loader()
            .library(LIBNAME)
            .addPreLoadCallback((dir, libname, oslibname) -> {
                if(LIBNAME.equals(libname))
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();
        Native.register(LIBNAME);

        pcv4j_ipc_logging_setLogLevel(kogLoglevel(LOGGER));
    }

    /**
     * Lookup the int based log level to pass to native logging calls given the
     * configuration of the Logger.
     */
    public static int kogLoglevel(final Logger logger) {
        // find the level
        final int logLevelSet;
        if(logger.isTraceEnabled())
            logLevelSet = LOG_LEVEL_TRACE;
        else if(logger.isDebugEnabled())
            logLevelSet = LOG_LEVEL_DEBUG;
        else if(logger.isInfoEnabled())
            logLevelSet = LOG_LEVEL_INFO;
        else if(logger.isWarnEnabled())
            logLevelSet = LOG_LEVEL_WARN;
        else if(logger.isErrorEnabled())
            logLevelSet = LOG_LEVEL_ERROR;
        else
            logLevelSet = LOG_LEVEL_FATAL;
        return logLevelSet;
    }

    // ===============================================================
    // Overall system management functionality

    /**
     * General utilities
     */
    public static native void pcv4j_ipc_logging_setLogLevel(int logLevel);

    /*
     * MatQueue
     */
    public static native long pilecv4j_ipc_create_shmQueue(String name);

    public static native void pilecv4j_ipc_destroy_shmQueue(long nativeRef);

    public static native long pilecv4j_ipc_shmQueue_create(long nativeRef, long size, int owner);

    public static native long pilecv4j_ipc_shmQueue_open(long nativeRef, int owner);

    public static native long pilecv4j_ipc_shmQueue_buffer(long nativeRef, long offset, PointerByReference owner);

    public static native long pilecv4j_ipc_shmQueue_bufferSize(long nativeRef, LongByReference owner);

    public static native long pilecv4j_ipc_shmQueue_lock(long nativeRef, long millis, int aggressive);

    public static native long pilecv4j_ipc_shmQueue_unlock(long nativeRef);

    public static native long pilecv4j_ipc_shmQueue_postMessage(long nativeRef);

    public static native long pilecv4j_ipc_shmQueue_unpostMessage(long nativeRef);

    public static native long pilecv4j_ipc_shmQueue_isMessageAvailable(long nativeRef, IntByReference result);

    /*
     * Error handling
     */
    public static native Pointer pcv4j_ipc_errHandling_errString(long code);

    public static native void pcv4j_ipc_errHandling_freeErrString(Pointer errStr);

    public static native long pcv4j_ipc_errHandling_getEAGAIN();

    public static native long pcv4j_ipc_errHandling_getOK();

}
