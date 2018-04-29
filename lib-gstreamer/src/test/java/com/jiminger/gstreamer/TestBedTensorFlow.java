package com.jiminger.gstreamer;

import static com.jiminger.gstreamer.util.GstUtils.instrument;
import static com.jiminger.gstreamer.util.GstUtils.printDetails;
import static com.jiminger.image.TensorUtils.getMatrix;
import static com.jiminger.image.TensorUtils.getScalar;
import static com.jiminger.image.TensorUtils.getVector;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.tensorflow.types.UInt8;

import com.jiminger.gstreamer.od.ObjectDetection;
import com.jiminger.image.CvRaster;
import com.jiminger.image.TensorUtils;

public class TestBedTensorFlow {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestBedTensorFlow.class);

    public static final double threshold = 0.5;
    public static final int fontHeight = 20;
    public static final double fontScale = 3;

    public static final String defaultSsdModel = "tensor/ssd_mobilenet_v1_coco_2017_11_17/frozen_inference_graph.pb";
    final static URI labelUri = new File(
            TestBedTensorFlow.class.getClassLoader().getResource("tensor/ssd_mobilenet_v1_coco_2017_11_17/mscoco_label_map.txt").getFile())
                    .toURI();

    public static void main(final String[] args) throws Exception {

        final URI modelUri = new File(
                TestBedTensorFlow.class.getClassLoader().getResource(defaultSsdModel)
                        .getFile()).toURI();

        // TODO: There is a protobufs problem mixing OpenCv and TensorFlow. TensorFlow uses
        // a much later version than the one COMPILED INTO (WTF?) opencv so we need the TensorFlow
        // library loaded first.
        final Graph graph = TensorUtils.inflate(Files.readAllBytes(Paths.get(modelUri)));

        // setGstLogLevel(Level.FINE);

        Gst.init(TestBedTensorFlow.class.getSimpleName(), args);

        // ====================================================================
        final List<String> labels = Files.readAllLines(Paths.get(labelUri), Charset.forName("UTF-8"));
        CvRaster.initOpenCv();

        final BreakoutFilter bin = new BreakoutFilter("od")
                .connectSlowFilter(bac -> {
                    final CvRaster raster = bac.raster;
                    final ByteBuffer bb = raster.underlying;
                    bb.rewind();
                    final int w = bac.width;
                    final int h = bac.height;

                    final List<ObjectDetection> det;
                    try (final Tensor<UInt8> tensor = Tensor.create(UInt8.class, new long[] { 1, h, w, 3 }, bb);) {
                        bb.rewind(); // need to rewind after being passed to create
                        det = executeGraph(graph, tensor);
                    }

                    raster.matAp(mat -> {
                        final int thickness = (int) (0.003d * mat.width());
                        final int fontShift = thickness + (int) (fontHeight * fontScale);
                        det.stream().filter(d -> d.probability > threshold)
                                .forEach(d -> {
                                    final int classification = d.classification;
                                    final String label = classification < labels.size() ? labels.get(classification)
                                            : Integer.toString(classification);
                                    Imgproc.putText(mat, label,
                                            new Point(d.xmin * mat.width() + thickness, d.ymin * mat.height() + fontShift),
                                            Core.FONT_HERSHEY_SIMPLEX, fontScale, new Scalar(0xff, 0xff, 0xff), thickness);
                                    Imgproc.rectangle(mat, new Point(d.xmin * mat.width(), d.ymin * mat.height()),
                                            new Point(d.xmax * mat.width(), d.ymax * mat.height()),
                                            new Scalar(0xff, 0xff, 0xff), thickness);
                                });
                    });

                    LOGGER.trace("Returning object detected Buffer");
                });

        final Pipeline pipe = new BinBuilder()
                // .delayed(new URIDecodeBin("source"))
                // .with("uri", BaseTest.STREAM.toString())
                // .with("uri", "rtsp://admin:greg0rmendel@10.1.1.20:554/")
                .make("v4l2src")
                .make("videoconvert")
                .caps("video/x-raw")
                .tee(
                        new Branch("b2_")
                                .make("queue")
                                .make("videoscale")
                                .make("videoconvert")
                                .caps("video/x-raw,width=640,height=480")
                                .make("xvimagesink")
                        // .make("queue")
                        // .make("theoraenc")
                        // .make("oggmux")
                        // .make("tcpserversink").with("host", "127.0.0.1").with("port", "8080")

                        ,
                        new Branch("b1_")
                                .make("queue")
                                .caps(new CapsBuilder("video/x-raw").addFormatConsideringEndian().build())
                                .add(bin)
                                .make("videoscale")
                                .make("videoconvert")
                                .caps(new CapsBuilder("video/x-raw").add("width=640,height=480").build())
                                .make("queue")
                                .make("xvimagesink").with("force-aspect-ratio", "true")
                // .make("theoraenc")
                // .make("oggmux")
                // .make("tcpserversink").with("host", "127.0.0.1").with("port", "8081")

                )

                .buildPipeline();

        instrument(pipe);
        pipe.play();

        Thread.sleep(5000);

        try (final PrintStream ps = new PrintStream(new File("/tmp/pipeline.txt"))) {
            printDetails(pipe, ps);
        }

        Gst.main();
    }

    @SuppressWarnings("unchecked")
    private static List<ObjectDetection> executeGraph(final Graph graph, final Tensor<UInt8> imageTensor) {
        // for (final Operation op : (Iterable<Operation>) (() -> graph.operations())) {
        // System.out.println(op.name());
        // }
        try (final Session s = new Session(graph);) {
            final Runner runner = s.runner();
            final List<Tensor<?>> result = runner
                    .feed("image_tensor:0", imageTensor)
                    .fetch("num_detections:0")
                    .fetch("detection_classes:0")
                    .fetch("detection_scores:0")
                    .fetch("detection_boxes:0")
                    .run();

            // System.out.println(result);
            // for (int i = 0; i < result.size(); i++) {
            // final Tensor<Float> tensor = (Tensor<Float>) result.get(i);
            // final Object res = toNativeArray(tensor, float.class);
            // if (tensor.shape().length > 1)
            // System.out.println(Arrays.deepToString((Object[]) res));
            // else
            // System.out.println(Arrays.toString((float[]) res));
            // }

            if (result.size() != 4)
                throw new IllegalStateException("Expected 4 tensors from object detection. Received " + result.size());

            // first tensor is num
            final int numdetec = (int) getScalar((Tensor<Float>) result.get(0));

            // this is the classification results
            final Tensor<Float> classificationTensor = (Tensor<Float>) result.get(1);

            // This tensor should be 1 x numdetec.
            final long[] shape = classificationTensor.shape();
            if (shape.length != 2 || shape[0] != 1 || shape[1] != numdetec)
                throw new IllegalStateException("Expected the classification Tensor to be of dimentions (1 x " + numdetec + ") but appears to be "
                        + Arrays.toString(shape));

            final float[] tmpf = getVector(classificationTensor);
            final int[] classifications = IntStream.range(0, tmpf.length)
                    .map(i -> (int) tmpf[i])
                    .toArray();

            // System.out.println("Classifications: " + Arrays.toString(classifications));

            final float[] probabilities = getVector((Tensor<Float>) result.get(2));

            // System.out.println("Probabilities: " + Arrays.toString(probabilities));

            final float[][] boxes = getMatrix((Tensor<Float>) result.get(3));

            // System.out.println("Bounding boxes: " + Arrays.deepToString(boxes));

            final List<ObjectDetection> detections = IntStream.range(0, numdetec)
                    .mapToObj(i -> {
                        final float[] box = boxes[i];
                        return new ObjectDetection(probabilities[i], classifications[i], box[0], box[1], box[2], box[3]);
                    })
                    .collect(Collectors.toList());

            // System.out.println("Detections:");
            // detections.stream().forEach(d -> System.out.println(d));
            return detections;
        }
    }
}
