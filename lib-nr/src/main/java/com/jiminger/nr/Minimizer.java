/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

package com.jiminger.nr;

import java.util.concurrent.atomic.AtomicReference;

import com.jiminger.util.NativePointerWrap;
import com.sun.jna.NativeLibrary;

import net.dempsy.util.library.NativeLibraryLoader;

public class Minimizer {
    public static final String LIBNAME = "utilities.jiminger.com";

    public static void init() {
        NativeLibraryLoader.loader()
                .library(LIBNAME)
                .addCallback((dir, libname, oslibname) -> {
                    NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
                })
                .load();
    }

    MinimizerAPI API = MinimizerAPI.API;

    static {
        init();
    }

    private final Func f;
    private double[] minVec;

    public static double ftol = 1.0e-10;

    // static lock ... JNA code used is not threadsafe
    public static Object lock = new Object();

    public Minimizer(final Func f) {
        this.f = f;
    }

    public double minimize(final double[] p)
            throws MinimizerException {
        final double[][] xi = newUnitMatrix(p.length);
        return minimize(p, xi);
    }

    public double minimize(final double[] p, final double[][] xi) throws MinimizerException {
        minVec = new double[p.length];
        synchronized (lock) {
            return dominimize_jna(f, p, xi, ftol, minVec);
        }
    }

    private double dominimize_jna(final Func f, final double[] pd, final double[][] xi, final double jftol, final double[] minVal) {
        final int n = xi.length;

        // check to make sure xi is square.
        final int col = xi[0] == null ? 0 : xi[0].length;
        if (n != col)
            throw new IllegalArgumentException("xi matrix needs to be square. It's currently " + n + " X " + col);

        final double[] xiflat = new double[n * n];
        for (int i = 0; i < n; i++)
            System.arraycopy(xi[i], 0, xiflat, i * n, n);
        final int[] status = new int[1];
        status[0] = 0;

        // temporary double array to hold values being passed to Func
        final double[] tmp = new double[n];

        // cheap mutable to detect and pass around the side exceptions thrown by Func
        final AtomicReference<RuntimeException> error = new AtomicReference<>(null);

        final double ret = MinimizerAPI.API.dominimize((x, p_status) -> {
            int xindex = 0;
            for (int i = 0; i < n; i++) {
                tmp[i] = x.getFloat(xindex);
                xindex += Float.BYTES;
            }
            try {
                final float retx = (float) f.func(tmp);
                return retx;
            } catch (final RuntimeException th) {
                error.set(th);
                p_status.setInt(0, 1);
                return 0.0f;
            }
        }, pd.length, pd, xiflat, jftol, minVal, status);

        if (error.get() != null)
            throw new MinimizerException("Exception ocurred in function being minimized.", error.get());

        if (status[0] != 0) {
            try (final NativePointerWrap message = new NativePointerWrap(MinimizerAPI.API.nrGetErrorMessage());) {
                final String msgStr = message.ptr.getString(0, "UTF-8");
                throw new MinimizerException("Powell mimimization failed with a non-zero status (" + status[0] + ") and message \"" + msgStr + "\"");
            }
        }

        return ret;
    }

    public double[] getFinalPostion() {
        return minVec;
    }

    @FunctionalInterface
    public interface Func {
        public double func(double[] x);
    }

    private double[][] newUnitMatrix(final int n) {
        final double[][] ret = new double[n][];
        for (int i = 0; i < n; i++) {
            ret[i] = new double[n];
            for (int j = 0; j < n; j++)
                ret[i][j] = (i == j) ? 1.0 : 0.0;
        }
        return ret;
    }
}
