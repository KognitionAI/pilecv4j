package ai.kognition.pilecv4j.image.geometry.transform;

import org.opencv.core.Point;

import net.dempsy.util.QuietCloseable;

@FunctionalInterface
public interface Transform2D extends QuietCloseable {

   public Point transform(final Point point);

   @Override
   default public void close() {}

}
