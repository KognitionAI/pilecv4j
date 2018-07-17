package com.jiminger.image;

import java.lang.reflect.Array;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Tensor;
import org.tensorflow.types.UInt8;

public class TensorUtils {
   public static final Logger LOGGER = LoggerFactory.getLogger(TensorUtils.class);

   // private final static Method fromTensorMethod;
   // private final static Method c;
   //
   // private static final Method getMethod(final Class<?> clazz, final Class[] params, final String name) {
   // Method m;
   // try {
   // m = clazz.getDeclaredMethod(name, params);
   // if (m != null)
   // m.setAccessible(true);
   // } catch (NoSuchMethodException | SecurityException e) {
   // m = null;
   // LOGGER.warn(
   // "Cannot provide \"zero copy\" access to TensorFlow because of either Java security issues or the internal
   // structure of the code is incompatible with this version of "
   // + TensorUtils.class.getName(),
   // e);
   // }
   // return m;
   // }
   //
   // static {
   // fromTensorMethod = getMethod(Tensor.class, new Class[] { long.class }, "fromHandle");
   // c = getMethod(DataType.class, new Class[] {}, "c");
   // }

   public static Tensor<UInt8> toTensor(final CvRaster raster) {
      // if (fromTensorMethod == null || c == null) {
      // LOGGER.info("CvRaster " + raster + " being converted to a Tensor cannot be done with a \"zero copy.\" This may
      // be somewhat inefficent.");
      return Tensor.create(UInt8.class, new long[] {1,raster.rows(),raster.cols(),raster.channels()}, raster.underlying());
      // } else {
      // int i;
      // try {
      // i = (Integer) c.invoke(DataType.fromClass(UInt8.class));
      // } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      // throw new RuntimeException(e);
      // }
      // final long nativeTensor = createNativeTensorFromAddress(i,
      // new long[] { 1, raster.rows, raster.cols, raster.channels }, raster.elemSize * raster.rows * raster.cols,
      // CvRasterNative._getDataAddress(raster.mat.nativeObj));
      // try {
      // return (Tensor<UInt8>) fromTensorMethod.invoke(null, nativeTensor);
      // } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      // throw new RuntimeException(e);
      // }
      // }
   }

   public static Graph inflate(final byte[] graphBytes) {
      final Graph graph = new Graph();
      graph.importGraphDef(graphBytes);
      return graph;
   }

   // private static native long createNativeTensorFromAddress(int type, long[] shape, long size, long buffer);

   private static <T, AT> Object toNativeArray(final Tensor<T> tensor, final Class<AT> componentType) {
      final int[] dimentions = LongStream.of(tensor.shape())
            .mapToInt(l -> (int)l)
            .toArray();

      final Object results = Array.newInstance(componentType, dimentions);

      tensor.copyTo(results);

      return results;
   }

   public static float getScalar(final Tensor<Float> tensor) {
      // expect a 1 dim array with 1 value.
      final float[] result = new float[1];
      tensor.copyTo(result);
      return result[0];
   }

   public static float[] getVector(final Tensor<Float> tensor) {
      // expect a 1 dim array with 1 value.
      final float[][] result = new float[1][(int)tensor.shape()[1]];
      tensor.copyTo(result);
      return result[0];
   }

   public static float[][] getMatrix(final Tensor<Float> tensor) {
      final float[][][] matrix = (float[][][])toNativeArray(tensor, float.class);
      return matrix[0];
   }

}
