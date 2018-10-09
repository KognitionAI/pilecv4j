package ai.kognition.pilecv4j.image.calc;

import java.util.stream.IntStream;

import ai.kognition.pilecv4j.image.CvRaster;
import ai.kognition.pilecv4j.image.CvRaster.PixelAggregate;

public class Histogram {

    public final int[][] histograms;

    private Histogram(final CvRaster raster) {
        histograms = new int[raster.channels()][CvRaster.numChannelElementValues(raster)];
    }

    public static PixelAggregate<Object, Histogram> makeAggregate(final CvRaster raster) {
        final int channels = raster.channels();
        final CvRaster.GetChannelValueAsInt channelValFetcher = CvRaster.channelValueFetcher(raster);

        return (final Histogram prev, final Object pixel, final int row, final int col) -> {
            IntStream.range(0, channels)
                    .forEach(channel -> prev.histograms[channel][channelValFetcher.get(pixel, channel)]++);
            return prev;
        };
    }

    public static Histogram makeInitialValue(final CvRaster raster) {
        return new Histogram(raster);
    }

    public static Histogram makeHistogram(final CvRaster raster) {
        return raster.reduce(makeInitialValue(raster), makeAggregate(raster));
    }

}
