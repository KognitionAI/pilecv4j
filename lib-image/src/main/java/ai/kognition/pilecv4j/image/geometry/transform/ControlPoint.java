package ai.kognition.pilecv4j.image.geometry.transform;

import org.opencv.core.Point;

public class ControlPoint {
   public final Point originalPoint;
   public final Point transformedPoint;

   @SuppressWarnings("unused")
   private ControlPoint() {
      originalPoint = null;
      transformedPoint = null;
   }

   public ControlPoint(final Point originalPoint, final Point transformedPoint) {
      this.originalPoint = originalPoint;
      this.transformedPoint = transformedPoint;
   }

   @Override
   public String toString() {
      return "ControlPoint [originalPoint=" + originalPoint + ", transformedPoint=" + transformedPoint + "]";
   }
}