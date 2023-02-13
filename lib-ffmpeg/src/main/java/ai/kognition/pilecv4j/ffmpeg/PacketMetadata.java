package ai.kognition.pilecv4j.ffmpeg;

public record PacketMetadata(int mediaType, int stream_index, int packetNumBytes, boolean isKeyFrame, long pts,
    long dts, int tbNum, int tbDen) {}
