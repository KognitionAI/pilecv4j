package ai.kognition.pilecv4j.tf;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.tensorflow.proto.framework.ConfigProto;
import org.tensorflow.types.TFloat32;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

// Interrogate the saved model in order to get the input/output
// Operation names using the process described here:
// https://stackoverflow.com/questions/59263406/how-to-find-operation-names-in-tensorflow-graph

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
                    .setPerProcessGpuMemoryFraction(0.04)
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

            // final Graph graph = mb.graph();
            // final Iterator<GraphOperation> itr = graph.operations();
            // while(itr.hasNext()) {
            // final GraphOperation e = itr.next();
            // System.out.println(e);
            // }

            final File dir = new File(IMAGE_DIR);
            while(true) {
                Arrays.stream(dir.listFiles()).forEach(f -> {

                    try(final CvMat mat = uncheck(() -> ImageFile.readMatFromFile(f.getAbsolutePath()));
                        final CvMat lb = Utils.letterbox(mat, MODEL_INPUT_DIM);
                        final CvMat rgb = chain(new CvMat(), m -> Imgproc.cvtColor(lb, m, Imgproc.COLOR_BGR2RGB));
                        CvMat maybe = chain(new CvMat(), m -> rgb.convertTo(m, CvType.CV_32FC3, 1 / 255.0));
                        CvMat ready = maybe.isContinuous() ? CvMat.shallowCopy(maybe) : CvMat.deepCopy(maybe);
                        Tensor tensor = TensorUtils.toTensor(ready, TFloat32.class);) {

                        id.update(lb);

                        // Interrogate the saved model in order to get the input/output
                        // Operation names using the process described here:
                        // https://stackoverflow.com/questions/59263406/how-to-find-operation-names-in-tensorflow-graph
                        final Runner runner = session.runner();

                        final List<Tensor> result = runner.feed("serving_default_input_1:0", tensor)
                            // .fetch("dense/kernel:0")
                            .fetch("StatefulPartitionedCall:0")
                            .run();

                        System.out.println(result.get(0).shape());
                        System.out.println(Arrays.toString(TensorUtils.getVector(result.get(0))));
                    }
                });
            }

        }
    }
}
