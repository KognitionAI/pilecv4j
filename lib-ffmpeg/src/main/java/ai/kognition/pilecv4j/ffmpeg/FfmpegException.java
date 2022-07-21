package ai.kognition.pilecv4j.ffmpeg;

public class FfmpegException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    public final long status;

    public FfmpegException(final String message) {
        super(message);
        this.status = 0;
    }

    public FfmpegException(final String message, final Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public FfmpegException(final long status, final String message) {
        super((status == 0) ? message : (sanitizeStatus(status) + ", " + message));
        this.status = status;
    }

    private static String sanitizeStatus(final long status) {
        if((status & 0xffffffff00000000L) == 0) { // not a pilecv4j status
            if((status & 0x0000000080000000L) != 0)
                return "AV status: " + (int)(status & ~0xffffffff00000000L);
            else
                return "AV status: " + (int)status;
        } else { // is a pilecv4j status
            return "Pilecv4j status: " + (int)(status >> 32);
        }
    }
}
