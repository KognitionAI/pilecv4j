package ai.kognition.pilecv4j.image.geometry;

public class LineSegment {
    public final Point p1;
    public final Point p2;
    public final Direction direction;

    /**
     * This is the quantified gradient direction.
     */
    public final byte gradientDirection;

    /**
     * Direction is whether or not to use the right hand rule (see http://mathworld.wolfram.com/Right-HandRule.html )
     * indicating the perpendicular direction is given by ((p2 - p1) / | p2 - p1 |) X [0, 0, 1]. For example:
     * 
     * if P1 = [0, 0]
     *    P2 = [0, 1]
     * 
     * then P2 - P1 = [0, 1].
     * Given the "Direction" = RIGHT, the perpendicular direction to the line would be: [0, 1, 0] X [0, 0, 1] =
     * 
     * | i   j  k |
     * | 0   1  0 |
     * | 0   0  1 |
     * 
     * which is = i ( 1*1 - 0*0) - j (0*1 - 0*0) + k (0*0 - 1*0) =
     *            i1 + j0 +k0 => (1, 0).
     *            
     * So, given a "RIGHT" direction, if you stand on P1 and look at P2, the direction of the perpendicular
     * will be from left to right. Stand on the origin, look "North" (toward P2 = [0, 1]) and the direction
     * of the perpendicular will be toward [0, 1] (from your left, to your right). 
     */
    public static enum Direction {
        LEFT,
        RIGHT;

        public static Direction FORWARD = RIGHT;
        public static Direction REVERSE = LEFT;
    }

    private final Point p2Trans;
    private final double p2TransMagSq;

    // This will tell the distance algorithm which dimension to use to
    // tell if the point being checked is off one end of the line segment.
    private final boolean xbiased;

    public LineSegment(final Point p1, final Point p2) {
        this(p1, p2, Direction.RIGHT);
    }

    public LineSegment(final Point p1, final Point p2, final Direction direction) {
        this.p1 = p1;
        this.p2 = p2;
        this.direction = direction;

        p2Trans = p2.subtract(p1);
        p2TransMagSq = p2Trans.magnitudeSquared();
        xbiased = Math.abs(p1.x() - p2.x()) > Math.abs(p1.y() - p2.y());

        final Point zcross = p2Trans.crossWithZ(direction == Direction.LEFT);
        gradientDirection = zcross.quantizedDirection();
    }

    public double distance(final Point x) {
        final Point xTrans = x.subtract(p1);

        // xTrans.p2Trans = |xTrans| |p2Trans| cos th
        // so the projection of x on the line from p1 to p2 is:
        // ((xTrans.p2Trans / |p2Trans|) * unit(p2Trans)) + p1 =
        // ((xTrans.p2Trans / |p2Trans|) * p2Trans /|p2Trans|) + p1 =
        // ((xTrans.p2Trans / |p2Trans|^2) * p2Trans) + p1

        final Point projection = p2Trans.multiply(xTrans.dot(p2Trans) / p2TransMagSq).add(p1);

        // is the point off the end of the line segment
        final Point closest;
        if (xbiased) { // We're x-biased
            if (p2Trans.x() > 0.0) { // ... and p2 is right of p1
                if (x.x() > p2.x()) // ... and x.x is outside of p2.x
                    closest = p2;
                else if (x.x() < p1.x()) // ... or x.x is outside of p1.x
                    closest = p1;
                else // ... otherwise.
                    closest = projection;
            } else { // ... p2 is left of p1
                if (x.x() < p2.x()) // ... and x is left of p2.
                    closest = p2;
                else if (x.x() > p1.x()) // ... and x is right of p1
                    closest = p1;
                else // ... otherwise
                    closest = projection;
            }
        } else { // we're y-biased
            if (p2Trans.y() > 0.0) { // ... and p2 is above p1
                if (x.y() > p2.y()) // ... and x is above p2
                    closest = p2;
                else if (x.y() < p1.y()) // ... or x is below p1
                    closest = p1;
                else // ... otherwise.
                    closest = projection;
            } else { // ... p2 is below p1
                if (x.y() < p2.y()) // ... and x is below p2.
                    closest = p2;
                else if (x.y() > p1.y()) // ... and x is above p1
                    closest = p1;
                else // ... otherwise
                    closest = projection;
            }
        }
        return x.distance(closest);
    }

    @Override
    public String toString() {
        return p1.toString() + "=>" + p2.toString();
    }
}