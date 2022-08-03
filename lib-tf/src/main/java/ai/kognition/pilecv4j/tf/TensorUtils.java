/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.tf;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.stream.LongStream;

import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.ByteDataBuffer;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.proto.framework.GraphDef;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.family.TType;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.CvRaster;

public class TensorUtils {
    public static final Logger LOGGER = LoggerFactory.getLogger(TensorUtils.class);

    public static Tensor toTensor(final CvRaster raster, final Class<? extends TType> clazz) {
        final Shape shape = Shape.of(new long[] {1,raster.rows(),raster.cols(),raster.channels()});
        final ByteBuffer bb = raster.underlying();
        bb.rewind();
        try(QuietCloseable qc = () -> bb.rewind();) {
            final ByteDataBuffer bdb = DataBuffers.of(bb);
            return Tensor.of(clazz, shape, bdb);
        }
    }

    public static Tensor toTensor(final CvMat mat, final Class<? extends TType> clazz) {
        return mat.rasterOp(raster -> {
            return TensorUtils.toTensor(raster, clazz);
        });
    }

    public static Graph inflate(final byte[] graphBytes) throws InvalidProtocolBufferException {
        final Graph graph = new Graph();
        final GraphDef gd = GraphDef.parseFrom(graphBytes);
        graph.importGraphDef(gd);
        return graph;
    }

    public static float getScalar(final Tensor tensor) {
        // expect a 1 dim array with 1 value.
        return ((TFloat32)tensor).getFloat();
    }

    public static float[] getVector(final Tensor tensor) {
        // expect a 1 dim array with 1 value.
        final int dim1 = (int)tensor.shape().asArray()[1];
        final float[][] result = new float[1][dim1];
        for(long i = 0; i < result.length; i++) {
            for(long j = 0; j < dim1; j++) {
                result[(int)i][(int)j] = ((TFloat32)tensor).getFloat(i, j);
            }
        }

        return result[0];
    }

    public static float[][] getMatrix(final Tensor tensor) {
        final int[] dimentions = LongStream.of(tensor.shape().asArray())
            .mapToInt(l -> (int)l)
            .toArray();
        final float[][][] matrix = (float[][][])Array.newInstance(float.class, dimentions);
        for(long i = 0; i < dimentions[0]; i++) {
            for(long j = 0; j < dimentions[1]; j++) {
                for(long k = 0; k < dimentions[2]; k++) {
                    matrix[(int)i][(int)j][(int)k] = ((TFloat32)tensor).getFloat(i, j, k);
                }
            }
        }
        return matrix[0];
    }

}
