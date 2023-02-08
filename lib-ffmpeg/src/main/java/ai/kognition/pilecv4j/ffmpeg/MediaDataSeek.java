package ai.kognition.pilecv4j.ffmpeg;

/**
 * This interface is used in both muxing and custom sources.
 */
@FunctionalInterface
public interface MediaDataSeek {
    public long seekBuffer(long offset, int whence);
}
