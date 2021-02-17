package ai.kognition.pilecv4j.gstreamer.util;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.freedesktop.gstreamer.Bin;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.gstreamer.BinManager;
import ai.kognition.pilecv4j.gstreamer.BreakoutFilter;
import ai.kognition.pilecv4j.gstreamer.CapsBuilder;

public class FrameCatcher implements QuietCloseable {
    private final List<Frame> frames = new LinkedList<>();
    private int frameCount = 0;
    private Bin bin;

    public static class Frame {
        public final byte[] data;
        public final int w;
        public final int h;

        public Frame(final byte[] data, final int w, final int h) {
            super();
            this.data = data;
            this.w = w;
            this.h = h;
        }
    }

    public FrameCatcher(final String name) {
        this(name, false);
    }

    @SuppressWarnings("resource")
    public FrameCatcher(final String name, final boolean keepFrames) {

        bin = new BinManager()
            .add(new BreakoutFilter(name)
                .filter(vf -> vf.rasterAp(raster -> {
                    final ByteBuffer bb = raster.underlying();
                    bb.rewind();
                    final byte[] data = new byte[raster.getNumBytes()];
                    bb.get(data);
                    if(keepFrames)
                        frames.add(new Frame(data, vf.width(), vf.height()));
                    frameCount++;
                })))
            .caps(new CapsBuilder("video/x-raw")
                .addFormatConsideringEndian()
                .build())
            .make("fakesink").with("sync", true)
            .buildBin();
    }

    public int numCaught() {
        return frameCount;
    }

    public List<Frame> frames() {
        return frames;
    }

    public Bin disown() {
        final Bin ret = bin;
        bin = null;
        return ret;
    }

    @Override
    public void close() {
        frames.clear();

        if(bin != null) {
            bin.dispose();
            disown();
        }
    }
}
