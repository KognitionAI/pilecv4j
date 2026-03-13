/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.ffmpeg;

/**
 * Measures the actual bitrate of a media stream by accumulating packet sizes
 * over a configurable time window. This is essential for streams where the
 * container metadata does not report a bitrate (e.g., VBR H.264 over RTSP).
 *
 * <p>Usage as a {@link PacketFilter} (passes all packets through, just measures):
 * <pre>{@code
 * BitrateMonitor monitor = new BitrateMonitor(5000); // 5-second window
 * mediaContext
 *     .chain("default")
 *     .filterPackets(monitor.asPacketFilter())
 *     .processVideoFrames(consumer);
 *
 * // Later, query the measured bitrate:
 * long bitsPerSec = monitor.getBitsPerSecond();
 * }</pre>
 *
 * <p>Or call {@link #addPacket(int)} directly from any packet-processing callback.
 */
public class BitrateMonitor {

    private final long windowMillis;

    private long windowStartTime = -1;
    private long totalBytes = 0;
    private long lastBitsPerSecond = 0;

    /**
     * @param windowMillis the measurement window in milliseconds. Bitrate is
     *     calculated and reset every time this window elapses. Typical values:
     *     5000–10000 ms.
     */
    public BitrateMonitor(final long windowMillis) {
        if (windowMillis <= 0)
            throw new IllegalArgumentException("windowMillis must be > 0");
        this.windowMillis = windowMillis;
    }

    /**
     * Convenience: 10-second default window.
     */
    public BitrateMonitor() {
        this(10_000);
    }

    /**
     * Record a packet of the given size. Call this on every packet you want
     * to include in the bitrate measurement.
     *
     * @param packetBytes size of the packet in bytes
     */
    public synchronized void addPacket(final int packetBytes) {
        final long now = System.currentTimeMillis();

        if (windowStartTime < 0) {
            windowStartTime = now;
            totalBytes = 0;
        }

        totalBytes += packetBytes;

        final long elapsed = now - windowStartTime;
        if (elapsed >= windowMillis) {
            lastBitsPerSecond = (totalBytes * 8 * 1000) / elapsed;
            // reset for next window
            windowStartTime = now;
            totalBytes = 0;
        }
    }

    /**
     * @return the measured bitrate in bits/second from the last completed
     *     measurement window, or 0 if no complete window has elapsed yet.
     */
    public synchronized long getBitsPerSecond() {
        return lastBitsPerSecond;
    }

    /**
     * @return the measured bitrate in kilobits/second (kbps).
     */
    public synchronized long getKbps() {
        return lastBitsPerSecond / 1000;
    }

    /**
     * @return the measured bitrate in megabits/second (Mbps).
     */
    public synchronized double getMbps() {
        return lastBitsPerSecond / 1_000_000.0;
    }

    /**
     * Creates a {@link PacketFilter} that passes all packets through (never filters)
     * but records their sizes for bitrate measurement. Attach this to a
     * {@link Ffmpeg.MediaProcessingChain} via {@code filterPackets()}.
     */
    public PacketFilter asPacketFilter() {
        return (mediaType, streamIndex, packetNumBytes, isKeyFrame, pts, dts, tbNum, tbDen) -> {
            addPacket(packetNumBytes);
            return true; // never filter — just observe
        };
    }

    /**
     * Creates a {@link PacketFilter} that only measures packets from the specified
     * stream index. All packets are still passed through.
     */
    public PacketFilter asPacketFilterForStream(final int targetStreamIndex) {
        return (mediaType, streamIndex, packetNumBytes, isKeyFrame, pts, dts, tbNum, tbDen) -> {
            if (streamIndex == targetStreamIndex)
                addPacket(packetNumBytes);
            return true;
        };
    }

    /**
     * Reset all accumulated data.
     */
    public synchronized void reset() {
        windowStartTime = -1;
        totalBytes = 0;
        lastBitsPerSecond = 0;
    }
}
