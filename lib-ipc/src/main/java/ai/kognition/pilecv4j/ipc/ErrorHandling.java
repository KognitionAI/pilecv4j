package ai.kognition.pilecv4j.ipc;

import com.sun.jna.Pointer;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.ipc.internal.IpcApi;

public class ErrorHandling {

    public static final long OK = IpcApi.pcv4j_ipc_errHandling_getOK();
    public static final long EAGAIN = IpcApi.pcv4j_ipc_errHandling_getEAGAIN();

    public static long throwIfNecessary(final long code, final boolean throwOnEAGAIN) {
        if(code != OK && (throwOnEAGAIN || code != EAGAIN)) {
            final Pointer errMsg = IpcApi.pcv4j_ipc_errHandling_errString(code);
            try(QuietCloseable qc = () -> IpcApi.pcv4j_ipc_errHandling_freeErrString(errMsg);) {
                if(Pointer.nativeValue(errMsg) == 0)
                    throw new IpcException("Bad Error Code", code);
                else
                    throw new IpcException(errMsg.getString(0L), code);
            }
        }
        return code;
    }

}
