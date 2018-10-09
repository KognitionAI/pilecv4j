/***********************************************************************
 * Legacy Film to DVD Project
 * Copyright (C) 2005 James F. Carroll
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 ****************************************************************************/

package ai.kognition.pilecv4j.nr;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import ai.kognition.pilecv4j.util.NativePointerWrap;

/**
 * <p>
 * This class encapsulates the running of
 * <a href="https://en.wikipedia.org/wiki/Powell's_method">Powell's Method</a> on a
 * given function in order to determine a local minimum.
 * </p>
 * 
 * <p>
 * The function to be minimized can have a domain of any dimension as it takes an array
 * of {@code double}s and returns the value that needs to be minimized.
 * </p>
 * 
 * <p>
 * For example, to minimize the function {@code (x - 2)^2 - 3} you would:
 * </p>
 * 
 * <pre>
 * {
 *    &#64;code
 *    final Minimizer m = new Minimizer(x -> ((x[0] - 2.0) * (x[0] - 2.0)) - 3.0);
 *    final double minVal = m.minimize(new double[] {-45.0});
 *    final double minParam = m.getFinalPostion()[0];
 * }
 * </pre>
 */
public class Minimizer {
   static {
      MinimizerAPI._init();
   }

   private final Func f;
   private double[] minVec;

   /**
    * Interface representing the function/lambda to be minimized.
    */
   @FunctionalInterface
   public interface Func {
      public double func(double[] x);
   }

   /**
    * Default float tolerance.
    */
   public static double ftol = 1.0e-10;

   /**
    * Construct the minimizer with the function to be minimized.
    */
   public Minimizer(final Func f) {
      this.f = f;
   }

   /**
    * Minimize the function that the {@link Minimizer} was instantiated with using the
    * identity matrix as the starting position.
    */
   public double minimize(final double[] p)
         throws MinimizerException {
      final double[][] xi = newUnitMatrix(p.length);
      return minimize(p, xi);
   }

   /**
    * Minimize the function that the {@link Minimizer} was instantiated with using the
    * supplied starting position.
    */
   public double minimize(final double[] p, final double[][] xi) throws MinimizerException {
      minVec = new double[p.length];
      return dominimize_jna(f, p, xi, ftol, minVec);
   }

   public static class FinalPosition {
      public final double error;
      public final double[] position;

      private FinalPosition(final double error, final double[] position) {
         this.error = error;
         this.position = position;
      }

      @Override
      public String toString() {
         return "[ minimized error: " + error + ", minimized solution: " + Arrays.toString(position) + "]";
      }
   }

   public static FinalPosition minimize(final Func functionToMinimize, final double[] startingPosition) {
      final Minimizer minimizer = new Minimizer(functionToMinimize);
      final double err = minimizer.minimize(startingPosition);
      return new FinalPosition(err, minimizer.getFinalPostion());
   }

   private double dominimize_jna(final Func f, final double[] pd, final double[][] xi, final double jftol, final double[] minVal) {
      final int n = xi.length;

      // check to make sure xi is square.
      final int col = xi[0] == null ? 0 : xi[0].length;
      if(n != col)
         throw new IllegalArgumentException("xi matrix needs to be square. It's currently " + n + " X " + col);

      final double[] xiflat = new double[n * n];
      for(int i = 0; i < n; i++)
         System.arraycopy(xi[i], 0, xiflat, i * n, n);
      final int[] status = new int[1];
      status[0] = 0;

      // temporary double array to hold values being passed to Func
      final double[] tmp = new double[n];

      // cheap mutable to detect and pass around the side exceptions thrown by Func
      final AtomicReference<RuntimeException> error = new AtomicReference<>(null);

      final double ret = MinimizerAPI.dominimize((x, p_status) -> {
         int xindex = 0;
         for(int i = 0; i < n; i++) {
            tmp[i] = x.getFloat(xindex);
            xindex += Float.BYTES;
         }
         try {
            final float retx = (float)f.func(tmp);
            return retx;
         } catch(final RuntimeException th) {
            error.set(th);
            p_status.setInt(0, 1);
            return 0.0f;
         }
      }, pd.length, pd, xiflat, jftol, minVal, status);

      if(error.get() != null)
         throw new MinimizerException("Exception ocurred in function being minimized.", error.get());

      if(status[0] != 0) {
         try (final NativePointerWrap message = new NativePointerWrap(MinimizerAPI.nrGetErrorMessage());) {
            final String msgStr = message.ptr.getString(0, "UTF-8");
            throw new MinimizerException("Powell mimimization failed with a non-zero status (" + status[0] + ") and message \"" + msgStr + "\"");
         }
      }

      return ret;
   }

   /**
    * Return the final domain value of the minimized solution.
    */
   public double[] getFinalPostion() {
      return minVec;
   }

   private double[][] newUnitMatrix(final int n) {
      final double[][] ret = new double[n][];
      for(int i = 0; i < n; i++) {
         ret[i] = new double[n];
         for(int j = 0; j < n; j++)
            ret[i][j] = (i == j) ? 1.0 : 0.0;
      }
      return ret;
   }
}
