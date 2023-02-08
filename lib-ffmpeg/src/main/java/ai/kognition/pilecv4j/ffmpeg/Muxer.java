package ai.kognition.pilecv4j.ffmpeg;

import java.nio.ByteBuffer;
import java.util.function.Function;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.create_muxer_from_java_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.seek_buffer_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.should_close_segment_callback;
import ai.kognition.pilecv4j.ffmpeg.internal.FfmpegApi.write_buffer_callback;

/**
 * Class the wraps an FFMpeg muxer. These are automatically created by all of the {@code createRemuxer} calls
 * as well as explicitly created by all {@code newMuxer} calls.
 */
public class Muxer implements QuietCloseable {
    final long nativeRef;
    private boolean skipCloseOnceForReturn = false;

    private Muxer(final long nativeRef) {
        this.nativeRef = nativeRef;
    }

    /**
     * This interface is used for custom output post-muxing
     */
    @FunctionalInterface
    public static interface WritePacket {
        public void handle(ByteBuffer packet, int len);
    }

    @Override
    public void close() {
        if(skipCloseOnceForReturn) {
            skipCloseOnceForReturn = false;
            return;
        }
        if(nativeRef != 0L)
            FfmpegApi.pcv4j_ffmpeg2_muxer_delete(nativeRef);
    }

    public Muxer returnMe() {
        skipCloseOnceForReturn = true;
        return this;
    }

    public static Muxer create(final String fmt, final String outputUri) {
        try(Muxer ret = new Muxer(FfmpegApi.pcv4j_ffmpeg2_defaultMuxer_create(fmt, outputUri, null, null));) {
            return ret.returnMe();
        }
    }

    public static Muxer create(final String outputUri) {
        return create(null, outputUri);
    }

    public static Muxer create(final String outputFormat, final WritePacket writer, final MediaDataSeek seek) {
        final Wbc wbc = new Wbc(writer);
        final seek_buffer_callback sbcb = seek != null ? new seek_buffer_callback() {
            @Override
            public long seek_buffer(final long offset, final int whence) {
                return seek.seekBuffer(offset, whence);
            }
        } : null;

        try(final var output = new CustomMuxer(FfmpegApi.pcv4j_ffmpeg2_defaultMuxer_create(outputFormat, null, wbc, sbcb), wbc, sbcb);) {
            // violation of the rule that objects should be usable once the constructor returns ... oh well,
            // at least it's private. The fix for this would be to have the Wbc hold the CustomOutput rather than
            // the other way around but ... not right now.
            wbc.bb = output.customBuffer();

            return output.returnMe();
        }
    }

    public static Muxer create(final String outputFormat, final WritePacket writer) {
        return create(outputFormat, writer, null);
    }

    public static Muxer create(final Function<Long, Muxer> segmentSupplier, final PacketFilter whenToSegment) {

        final create_muxer_from_java_callback p1 = (final long muxerNumber, final LongByReference muxerOut) -> {
            final Muxer next = segmentSupplier.apply(muxerNumber);
            muxerOut.setValue(next.nativeRef);
            return 0;
        };

        final should_close_segment_callback p2 = (final int mediaType, final int stream_index, final int packetNumBytes, final int isKeyFrame, final long pts,
            final long dts, final int tbNum, final int tbDen) -> {
            return whenToSegment.test(mediaType, stream_index, packetNumBytes, isKeyFrame == 0 ? false : true, pts, dts, tbNum, tbDen) ? 1 : 0;
        };

        return new SegmentedMuxer(FfmpegApi.pcv4j_ffmpeg2_segmentedMuxer_create(p1, p2), p1, p2);
    }

    private static class SegmentedMuxer extends Muxer {
        // ======================================================================
        // JNA will only hold a weak reference to the callbacks passed in
        // so if we dynamically allocate them then they will be garbage collected.
        // In order to prevent that, we're keeping strong references to them.
        // These are not private in order to avoid any possibility that the
        // JVM optimized them out since they aren't read anywhere in this code.
        @SuppressWarnings("unused") public create_muxer_from_java_callback strongRefW = null;
        @SuppressWarnings("unused") public should_close_segment_callback strongRefF = null;
        // ======================================================================

        private SegmentedMuxer(final long nativeRef, final create_muxer_from_java_callback write, final should_close_segment_callback sbcb) {
            super(nativeRef);
            strongRefW = write;
            strongRefF = sbcb;
        }
    }

    private static class CustomMuxer extends Muxer {
        // ======================================================================
        // JNA will only hold a weak reference to the callbacks passed in
        // so if we dynamically allocate them then they will be garbage collected.
        // In order to prevent that, we're keeping strong references to them.
        // These are not private in order to avoid any possibility that the
        // JVM optimized them out since they aren't read anywhere in this code.
        @SuppressWarnings("unused") public write_buffer_callback strongRefW = null;
        @SuppressWarnings("unused") public seek_buffer_callback strongRefS = null;
        // ======================================================================

        private CustomMuxer(final long nativeRef, final write_buffer_callback write, final seek_buffer_callback sbcb) {
            super(nativeRef);
            strongRefW = write;
            strongRefS = sbcb;
        }

        private ByteBuffer customBuffer() {
            final Pointer value = FfmpegApi.pcv4j_ffmpeg2_defaultMuxer_buffer(nativeRef);
            final int bufSize = FfmpegApi.pcv4j_ffmpeg2_defaultMuxer_bufferSize(nativeRef);
            return value.getByteBuffer(0, bufSize);
        }
    }

    private static class Wbc implements write_buffer_callback {
        ByteBuffer bb;
        final WritePacket writer;

        private Wbc(final WritePacket writer) {
            this.writer = writer;
        }

        @Override
        public long write_buffer(final int numBytesToWrite) {
            writer.handle(bb, numBytesToWrite);
            return 0L;
        }
    }
}
