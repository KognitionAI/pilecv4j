package ai.kognition.pilecv4j.ffmpeg;

/**
 * Used for filtering packets on the input stream and also for deciding when to cut video segments
 * in a segmenting Muxer
 */
@FunctionalInterface
public interface PacketFilter {
    /**
     * @return given the details, should this packet be let through
     */
    public boolean test(final int mediaType, final int stream_index, final int packetNumBytes, final boolean isKeyFrame, final long pts,
        final long dts, final int tbNum, final int tbDen);
}