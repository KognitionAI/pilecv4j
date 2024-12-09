package ai.kognition.pilecv4j.tf;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;
import org.opencv.core.CvType;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Session.Runner;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.proto.ConfigProto;
import org.tensorflow.types.TFloat32;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.Utils.LetterboxDetails;
import ai.kognition.pilecv4j.image.display.ImageDisplay;
import ai.kognition.pilecv4j.util.DetermineShowFlag;

// Interrogate the saved model in order to get the input/output
// Operation names using the process described here:
// https://stackoverflow.com/questions/59263406/how-to-find-operation-names-in-tensorflow-graph
// import org.tensorflow.Graph;
// import org.tensorflow.GraphOperation;
// import java.util.Iterator;

//@Ignore
public class TfTest {
    public final static Logger LOG = LoggerFactory.getLogger(TfTest.class);

    public final static boolean SHOW = DetermineShowFlag.SHOW;

    // public static final String MODEL = "/data/jim/kog/data/EV-models.testing/saved_ev_model_efficientnetv2-xl-21k";
    // public static final int MODEL_INPUT_DIM = 512;

    public static final String MODEL = "/data/jim/kog/data/EV-models.testing/saved_ev_model_efficientnetv2-s-21k";
    public static final int MODEL_INPUT_DIM = 384;

    public static final String IMAGE_DIR = "/data/jim/kog/data/EV unlabeled";

    @Test
    public void test() throws Exception {
        LOG.info("TensorFlow version: " + TensorFlow.version());

        // Create GPU options using the new API
        ConfigProto config = ConfigProto.newBuilder()
            .setGpuOptions(ConfigProto.GPUOptions.newBuilder()
                .setAllowGrowth(true)
                .setPerProcessGpuMemoryFraction(0.4)
                .build())
            .build();

        // Load the model using the new SavedModelBundle API
        try (SavedModelBundle mb = SavedModelBundle.loader(MODEL)
                .withTags("serve")
                .withConfigProto(config)
                .load();
             Session session = mb.session();
             ImageDisplay id = SHOW ? new ImageDisplay.Builder().build() : null) {

            LOG.info("Model loaded");

            File dir = new File(IMAGE_DIR);
            for (int i = 0; i < 2; i++) {
                Arrays.stream(dir.listFiles()).forEach(f -> {
                    LOG.info("Testing: " + f.getAbsolutePath());

                    try (CvMat mat = uncheck(() -> ImageFile.readMatFromFile(f.getAbsolutePath()));
                         LetterboxDetails lbd = Utils.letterbox(mat, MODEL_INPUT_DIM);
                         CvMat rgb = chain(new CvMat(), m -> Imgproc.cvtColor(lbd.mat(), m, Imgproc.COLOR_BGR2RGB));
                         CvMat maybe = chain(new CvMat(), m -> rgb.convertTo(m, CvType.CV_32FC3, 1 / 255.0));
                         CvMat ready = maybe.isContinuous() ? CvMat.shallowCopy(maybe) : CvMat.deepCopy(maybe);
                         Tensor tensor = TensorUtils.toTensor(ready, TFloat32.class)) {

                        LOG.trace("Loaded into tensor");

                        if (id != null) {
                            id.update(lbd.mat());
                        }

                        Runner runner = session.runner();
                        LOG.trace("Running model");
                        
                        var result = runner
                            .feed("serving_default_input_1:0", tensor)
                            .fetch("StatefulPartitionedCall:0")
                            .run();

                        LOG.trace("Extracting results");
                        LOG.debug("{}", result.get(0).shape());
                        LOG.debug(Arrays.toString(TensorUtils.getVector(result.get(0))));
                        
                        // Make sure to close the result tensors
                        result.forEach(Tensor::close);
                    }
                });
            }
        }
    }
}