package com.jiminger.gstreamer.od;

public class ObjectDetection {
    public final float probability;
    public final int classification;
    public final float ymin;
    public final float xmin;
    public final float ymax;
    public final float xmax;

    public ObjectDetection(final float probability, final int classification, final float ymin, final float xmin, final float ymax,
            final float xmax) {
        this.probability = probability;
        this.classification = classification;
        this.ymin = ymin;
        this.xmin = xmin;
        this.ymax = ymax;
        this.xmax = xmax;
    }

    @Override
    public String toString() {
        return "{ class: " + classification + " with probability " + (probability * 100.0) + " at [ ymin=" + ymin + ", xmin=" + xmin + ", ymax="
                + ymax + ", xmax=" + xmax + "] }";
    }
}