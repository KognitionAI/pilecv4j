package ai.kognition.pilecv4j.tf;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;
import org.junit.Ignore;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.TensorFlow;
import org.tensorflow.Tensor;
import org.tensorflow.proto.ConfigProto;
import org.tensorflow.types.TFloat32;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.Utils.LetterboxDetails;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

// Interrogate the saved model in order to get the input/output
// Operation names using the process described here:
// https://stackoverflow.com/questions/59263406/how-to-find-operation-names-in-tensorflow-graph
// import org.tensorflow.Graph;
// import org.tensorflow.GraphOperation;
// import java.util.Iterator;

@Ignore
public class TfTest {

    // public static final String MODEL = "/data/jim/kog/data/EV-models.testing/saved_ev_model_efficientnetv2-xl-21k";
    // public static final int MODEL_INPUT_DIM = 512;

    public static final String MODEL = "/data/jim/kog/data/EV-models.testing/saved_ev_model_efficientnetv2-s-21k";
    public static final int MODEL_INPUT_DIM = 384;

    // public static final String IMAGE = "/data/jim/kog/data/EV unlabeled/1000.jpg";
    public static final String IMAGE_DIR = "/data/jim/kog/data/EV unlabeled";
    // public static final String IMAGE_DIR = "/tmp/image";

    @Test
    public void test() throws Exception {

        System.out.println("TensorFlow version: " + TensorFlow.version());

        final ConfigProto.Builder b = ConfigProto.newBuilder();

        // final VirtualDevices vd = VirtualDevices.newBuilder()
        // .setMemoryLimitMb(0, 128)
        // .build();
        //
        // final Experimental e = Experimental.newBuilder()
        // .setVirtualDevices(0, vd)
        // .build();

        final ConfigProto p = b
            .setGpuOptions(

                b.getGpuOptionsBuilder()
                    .setAllowGrowth(true)
                    .setPerProcessGpuMemoryFraction(0.4)
                    // .setExperimental(e)
                    .build()

            )
            .build();

        try(final SavedModelBundle mb = SavedModelBundle.loader(MODEL)
            .withTags("serve")
            .withConfigProto(p)
            .load();
            final Session session = mb.session();
            final ImageDisplay id = new ImageDisplay.Builder().build();) {

            System.out.println("Model loaded");

            // final Graph graph = mb.graph();
            // final Iterator<GraphOperation> itr = graph.operations();
            // while(itr.hasNext()) {
            // final GraphOperation e = itr.next();
            // System.out.println(e);
            // }

            final File dir = new File(IMAGE_DIR);
            while(true) {
                Arrays.stream(dir.listFiles()).forEach(f -> {
                    System.out.println("Testing: " + f.getAbsolutePath());

                    try(final CvMat mat = uncheck(() -> ImageFile.readMatFromFile(f.getAbsolutePath()));
                        final LetterboxDetails lbd = Utils.letterbox(mat, MODEL_INPUT_DIM);
                        final CvMat rgb = chain(new CvMat(), m -> Imgproc.cvtColor(lbd.mat(), m, Imgproc.COLOR_BGR2RGB));
                        CvMat maybe = chain(new CvMat(), m -> rgb.convertTo(m, CvType.CV_32FC3, 1 / 255.0));
                        CvMat ready = maybe.isContinuous() ? CvMat.shallowCopy(maybe) : CvMat.deepCopy(maybe);
                        Tensor tensor = TensorUtils.toTensor(ready, TFloat32.class);) {

                        System.out.println("Loaded into tensor");

                        id.update(lbd.mat());

                        // Interrogate the saved model in order to get the input/output
                        // Operation names using the process described here:
                        // https://stackoverflow.com/questions/59263406/how-to-find-operation-names-in-tensorflow-graph
                        final Runner runner = session.runner();

                        System.out.println("Running model");
                        final var result = runner.feed("serving_default_input_1:0", tensor)
                            // .fetch("dense/kernel:0")
                            .fetch("StatefulPartitionedCall:0")
                            .run();

                        System.out.println("Extracting results");
                        System.out.println(result.get(0).shape());
                        System.out.println(Arrays.toString(TensorUtils.getVector(result.get(0))));
                    }
                });
            }

        }
    }
}
