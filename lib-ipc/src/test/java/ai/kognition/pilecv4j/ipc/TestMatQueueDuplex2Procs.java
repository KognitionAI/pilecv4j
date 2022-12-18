package ai.kognition.pilecv4j.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.CvType;

import net.dempsy.utils.test.ConditionPoll;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;

// This needs to be run manually by starting the testClient and testServer tests in separate processes.
@Ignore
public class TestMatQueueDuplex2Procs {

//    public static final String TEST_IMAGE = "celebs/albert-einstein.jpg";
    public static final String TEST_IMAGE = "resized.bmp";
    public static final long NUM_MESSAGES = 50000;

    @Test
    public void testServer() throws Exception {
        long size = -1;
        int type = -1;
        int rows = -1;
        int cols = -1;
        try(final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {
            size = mat.total() * mat.elemSize();
            type = mat.type();
            rows = mat.rows();
            cols = mat.cols();
            System.out.println("Mat:" + mat);
            System.out.println("data size:" + size);
        }

        try(final ShmQueue matqueue = new ShmQueue("TEST");) {
            matqueue.create(size, true, 2);
            long startTime = 0;
            int count = 0;
            for(int i = 0; i < NUM_MESSAGES; i++) {
                // read the message
                while(!matqueue.isMessageAvailable(0))
                    Thread.yield();
                try(var m = matqueue.accessAsMat(rows, cols, type);) {
                    assertTrue(matqueue.isMessageAvailable(0));
                    assertNotNull(m);
                    // copy the data
                    try(CvMat copy = new CvMat();) {
                        m.copyTo(copy);
                        m.unpost(0);
                    }
                }
                // write a response
                while(matqueue.isMessageAvailable(1)) // we want there to be a space for the message
                    Thread.yield();
                try(var m = matqueue.accessAsMat(1, 1, CvType.CV_64FC1);) {
                    m.put(0, 0, (double)count);
                    m.post(1);
                }
                if(count == 0)
                    startTime = System.currentTimeMillis();
                count++;
            }
            final long endTime = System.currentTimeMillis();
            System.out.println(
                "Received:" + count + " in " + (endTime - startTime) + " millis: " + ((count * 1000) / (endTime - startTime)) + " message per second.");
        }
    }

    @Test
    public void testClient() throws Exception {
        try(final ShmQueue matqueue = new ShmQueue("TEST");
            final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {

            assertTrue(ConditionPoll.poll(60000, o -> matqueue.open(false)));
            final long startTime = System.currentTimeMillis();
            int count = 0;
            for(int i = 0; i < NUM_MESSAGES; i++) {
                while(matqueue.isMessageAvailable(0)) // we want there to be a space for the message
                    Thread.yield();
                try(var m = matqueue.accessAsMat(mat.rows(), mat.cols(), mat.type());) {
                    mat.copyTo(m);
                    m.post(0);
                }
                // wait for response
                while(!matqueue.isMessageAvailable(1))
                    Thread.yield();
                try(var m = matqueue.accessAsMat(1, 1, CvType.CV_64FC1);) {
                    assertTrue(matqueue.isMessageAvailable(1));
                    assertNotNull(m);
                    final long resp = (long)m.get(0, 0)[0];
                    m.unpost(1);
                    assertEquals(count, resp);
                }
                count++;
            }
            final long endTime = System.currentTimeMillis();
            System.out.println(
                "Sent:" + count + " in " + (endTime - startTime) + " millis: " + ((count * 1000) / (endTime - startTime)) + " message per second.");
        }

    }
}
