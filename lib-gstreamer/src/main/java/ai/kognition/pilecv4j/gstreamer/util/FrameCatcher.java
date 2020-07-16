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
    public final List<Frame> frames = new LinkedList<>();
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

    @SuppressWarnings("resource")
    public FrameCatcher(final String name) {
        bin = new BinManager()
            .add(new BreakoutFilter(name)
                .filter(vf -> vf.rasterAp(raster -> {
                    final ByteBuffer bb = raster.underlying();
                    bb.rewind();
                    final byte[] data = new byte[raster.getNumBytes()];
                    bb.get(data);
                    frames.add(new Frame(data, vf.width(), vf.height()));
                })))
            .caps(new CapsBuilder("video/x-raw")
                .addFormatConsideringEndian()
                .build())
            .make("fakesink").with("sync", true)
            .buildBin();
    }

    public Bin disown() {
        final Bin ret = bin;
        bin = null;
        return ret;
    }

    @Override
    public void close() {
        if(bin != null) {
            bin.dispose();
            disown();
        }
    }
}
