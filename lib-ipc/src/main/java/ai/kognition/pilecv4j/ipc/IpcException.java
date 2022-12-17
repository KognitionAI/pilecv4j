package ai.kognition.pilecv4j.ipc;

public class IpcException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IpcException(final String errStr, final long errCode) {
        super(String.format("Error(0x%016x): %s", errCode, errStr));
    }

    public IpcException(final String msg) {
        super(msg);
    }

}
