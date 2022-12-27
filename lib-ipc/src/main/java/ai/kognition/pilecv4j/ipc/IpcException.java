package ai.kognition.pilecv4j.ipc;

public class IpcException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * This will hold the value of the underlying native error code if
     * this exception was generated from a native library return code.
     * Otherwise it will be set to {@link #NOT_NATIVE_ERROR_CODE}
     */
    public final long nativeErrCode;

    /**
     * A {@link #nativeErrCode} set to this means the error wasn't generated
     * from the native library.
     */
    public static final long NOT_NATIVE_ERROR_CODE = -1;

    IpcException(final String errStr, final long errCode) {
        super(String.format("Error(0x%016x): %s", errCode, errStr));
        this.nativeErrCode = errCode;
    }

    IpcException(final String msg) {
        super(msg);
        this.nativeErrCode = NOT_NATIVE_ERROR_CODE;
    }

}
