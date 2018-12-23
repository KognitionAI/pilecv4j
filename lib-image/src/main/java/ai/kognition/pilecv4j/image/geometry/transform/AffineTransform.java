package ai.kognition.pilecv4j.image.geometry.transform;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.Utils;

public class AffineTransform implements Transform2D {

   private final double tx;
   private final double ty;
   private final double sa;
   private final double sb;
   private final double sc;
   private final double sd;

   public AffineTransform(final ControlPoints cps) {
      final Point[] src = new Point[cps.controlPoints.length];
      final Point[] dst = new Point[cps.controlPoints.length];

      int index = 0;
      for(final ControlPoint cp: cps.controlPoints) {
         src[index] = cp.originalPoint;
         dst[index++] = cp.transformedPoint;
      }

      final double[][] transform;
      try (CvMat cvmat = CvMat.move(Imgproc.getAffineTransform(new MatOfPoint2f(src), new MatOfPoint2f(dst)));) {
         transform = Utils.to2dDoubleArray(cvmat);
      }

      sa = transform[0][0];
      sb = transform[0][1];
      sc = transform[1][0];
      sd = transform[1][1];
      tx = transform[0][2];
      ty = transform[1][2];
   }

   @Override
   public Point transform(final Point point) {
      final double x = point.x;
      final double y = point.y;
      return new Point(x * sa + y * sb + tx, x * sc + y * sd + ty);
   }
}
