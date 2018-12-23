package ai.kognition.pilecv4j.image.geometry;

public class SimplePoint implements Point {
   private final double r;
   private final double c;

   // Serialization
   @SuppressWarnings("unused")
   private SimplePoint() {
      r = c = -1.0;
   }

   public SimplePoint(final double r, final double c) {
      this.r = r;
      this.c = c;
   }

   @Override
   public double getRow() {
      return r;
   }

   @Override
   public double getCol() {
      return c;
   }

   @Override
   public String toString() {
      return Point.toString(this);
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      long temp;
      temp = Double.doubleToLongBits(c);
      result = prime * result + (int)(temp ^ (temp >>> 32));
      temp = Double.doubleToLongBits(r);
      result = prime * result + (int)(temp ^ (temp >>> 32));
      return result;
   }

   @Override
   public boolean equals(final Object obj) {
      if(this == obj) return true;
      if(obj == null) return false;
      if(getClass() != obj.getClass()) return false;
      final SimplePoint other = (SimplePoint)obj;
      if(Double.doubleToLongBits(c) != Double.doubleToLongBits(other.c)) return false;
      if(Double.doubleToLongBits(r) != Double.doubleToLongBits(other.r)) return false;
      return true;
   }

}
