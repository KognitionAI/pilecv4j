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
 import org.tensorflow.ndarray.buffer.DataBuffers;
 import org.tensorflow.proto.framework.GraphDef;
 import org.tensorflow.types.TFloat32;
 import org.tensorflow.types.family.TType;
 
 import net.dempsy.util.QuietCloseable;
 import ai.kognition.pilecv4j.image.CvMat;
 
 public class TensorUtils {
     public static final Logger LOGGER = LoggerFactory.getLogger(TensorUtils.class);
 
     public static Tensor toTensor(final CvMat mat, final Class<? extends TType> clazz) {
         return mat.bulkAccessOp(bb -> TensorUtils.toTensor(bb, mat.rows(), mat.cols(), mat.channels(), clazz));
     }
 
     public static Graph inflate(final byte[] graphBytes) throws InvalidProtocolBufferException {
         Graph graph = new Graph();
         try {
             // Parse the GraphDef using the new proto format
             GraphDef graphDef = GraphDef.parseFrom(graphBytes);
             // Import the GraphDef using byte array
             graph.importGraphDef(graphDef.toByteArray());
             return graph;
         } catch (Exception e) {
             graph.close();
             throw e;
         }
     }
 
     public static float getScalar(final Tensor tensor) {
         if (!(tensor instanceof TFloat32)) {
             throw new IllegalArgumentException("Tensor must be of type TFloat32");
         }
         TFloat32 tFloat = (TFloat32) tensor;
         return tFloat.getFloat();
     }
 
     public static float[] getVector(final Tensor tensor) {
         if (!(tensor instanceof TFloat32)) {
             throw new IllegalArgumentException("Tensor must be of type TFloat32");
         }
         TFloat32 tFloat = (TFloat32) tensor;
         long[] shape = tensor.shape().asArray();
         if (shape.length != 2) {
             throw new IllegalArgumentException("Expected 2D tensor for vector extraction");
         }
         
         int dim1 = (int) shape[1];
         float[][] result = new float[1][dim1];
         
         for (int i = 0; i < 1; i++) {
             for (int j = 0; j < dim1; j++) {
                 result[i][j] = tFloat.getFloat(i, j);
             }
         }
         return result[0];
     }
 
     public static float[][] getMatrix(final Tensor tensor) {
         if (!(tensor instanceof TFloat32)) {
             throw new IllegalArgumentException("Tensor must be of type TFloat32");
         }
         TFloat32 tFloat = (TFloat32) tensor;
         
         int[] dimensions = LongStream.of(tensor.shape().asArray())
             .mapToInt(l -> (int) l)
             .toArray();
             
         float[][][] matrix = (float[][][]) Array.newInstance(float.class, dimensions);
         
         for (int i = 0; i < dimensions[0]; i++) {
             for (int j = 0; j < dimensions[1]; j++) {
                 for (int k = 0; k < dimensions[2]; k++) {
                     matrix[i][j][k] = tFloat.getFloat(i, j, k);
                 }
             }
         }
         return matrix[0];
     }
 
     private static Tensor toTensor(final ByteBuffer bb, final int rows, final int cols, 
             final int channels, final Class<? extends TType> clazz) {
         Shape shape = Shape.of(1, rows, cols, channels);
         bb.rewind();
         
         try (QuietCloseable qc = () -> bb.rewind()) {
             if (clazz == TFloat32.class) {
                 return TFloat32.tensorOf(shape, data -> {
                     // Copy data from ByteBuffer to tensor
                     for (int i = 0; i < bb.capacity() / 4; i++) {
                         data.setFloat(bb.getFloat(), i);
                     }
                 });
             } else {
                 throw new IllegalArgumentException("Unsupported tensor type: " + clazz.getName());
             }
         }
     }
 }