package com.jiminger.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    // "Cannot provide \"zero copy\" access to TensorFlow because of either Java security issues or the internal structure of the code is incompatible with this version of "
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
        // LOGGER.info("CvRaster " + raster + " being converted to a Tensor cannot be done with a \"zero copy.\" This may be somewhat inefficent.");
        return Tensor.create(UInt8.class, new long[] { 1, raster.rows, raster.cols, raster.channels }, raster.dataAsByteBuffer());
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

    // private static native long createNativeTensorFromAddress(int type, long[] shape, long size, long buffer);

}
