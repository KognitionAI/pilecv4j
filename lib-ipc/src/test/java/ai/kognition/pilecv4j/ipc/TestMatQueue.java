package ai.kognition.pilecv4j.ipc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.opencv.core.CvType;

import net.dempsy.utils.test.ConditionPoll;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;

public class TestMatQueue {

    public static final String TEST_IMAGE = "celebs/albert-einstein.jpg";

    @Test
    public void testSimple() throws Exception {
        try(MatQueue matqueue = new MatQueue("TEST");) {
            assertTrue(matqueue.create(100000, false));
            assertTrue(matqueue.open(false));
        }
    }

    @Test
    public void testPass() throws Exception {
        try(MatQueue matqueue = new MatQueue("TEST");
            final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {

            assertTrue(matqueue.create(mat, true));
            try(CvMat shm = matqueue.tryGetWriteView(mat.total() * mat.elemSize(), CvType.makeType(mat.depth(), 1));) {
                assertNotNull(shm);
                mat.copyTo(shm);
            }

            try(CvMat result = matqueue.tryGetReadView(mat.total() * mat.channels(), CvType.makeType(mat.depth(), 1));
                CvMat reshaped = result == null ? null : CvMat.move(mat.reshape(mat.channels(), mat.rows()));) {
                assertNotNull(reshaped);
                assertTrue(mat.rasterOp(matRaster -> reshaped.rasterOp(resRaster -> matRaster.equals(resRaster))));
            }
        }
    }

    public static final long NUM_MESSAGES = 50000;

    @Test
    public void testClientServer() throws Exception {
        long size = -1;
        long numElements = -1;
        int type = -1;
        try(final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {
            size = mat.total() * mat.elemSize();
            numElements = mat.total();
            type = mat.type();
            System.out.println("Mat:" + mat);
            System.out.println("data size:" + size);
        }

        final long fsize = size;
        final long fnumElements = numElements;
        final int ftype = type;

        final AtomicBoolean serverException = new AtomicBoolean(false);

        final var server = new Thread(() -> {
            try(final MatQueue matqueue = new MatQueue("TEST");
                final CvMat result = new CvMat();) {
                assertTrue(matqueue.create(fsize, true));
                long startTime = 0;
                int count = 0;
                for(int i = 0; i < NUM_MESSAGES; i++) {
                    try(var m = matqueue.copyReadView(fnumElements, ftype);) {
                        assertNotNull(m);
                    }
                    if(count == 0)
                        startTime = System.currentTimeMillis();
                    count++;
                }
                final long endTime = System.currentTimeMillis();
                System.out.println(
                    "Received:" + count + " in " + (endTime - startTime) + " millis: " + ((count * 1000) / (endTime - startTime)) + " message per second.");
            } catch(final RuntimeException rte) {
                rte.printStackTrace();
                serverException.set(true);
            }
        });
        server.start();

        try(final MatQueue matqueue = new MatQueue("TEST");
            final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {

            assertTrue(ConditionPoll.poll(o -> matqueue.open(false)));
            final long startTime = System.currentTimeMillis();
            int count = 0;
            for(int i = 0; i < NUM_MESSAGES; i++) {
                assertTrue(matqueue.copyWriteView(mat));
                count++;
            }
            final long endTime = System.currentTimeMillis();
            System.out.println(
                "Sent:" + count + " in " + (endTime - startTime) + " millis: " + ((count * 1000) / (endTime - startTime)) + " message per second.");
        }

        assertTrue(ConditionPoll.poll(server, t -> !t.isAlive()));
        assertFalse(serverException.get());
    }
}
