package ai.kognition.pilecv4j.image.geometry.transform;

public class ControlPoints {
   public final ControlPoint[] controlPoints;

   @SuppressWarnings("unused")
   private ControlPoints() {
      controlPoints = null;
   }

   public ControlPoints(final ControlPoint[] controlPoints) {
      this.controlPoints = controlPoints;
   }
}