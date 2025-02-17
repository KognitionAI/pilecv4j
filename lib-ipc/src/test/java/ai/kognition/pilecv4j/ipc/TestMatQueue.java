package ai.kognition.pilecv4j.ipc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Ignore;
import org.junit.Test;
import org.opencv.core.CvType;

import net.dempsy.utils.test.ConditionPoll;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;

public class TestMatQueue {

    public static final String TEST_IMAGE = "resized.bmp";

    // a bit of a hack just for the test but this will need to be kept in sync with
    // the enum in errHandling.h
    public static final long ALREADY_OPEN_ERROR_CODE = (0x100000000L | 0x05L);

    @Test
    public void testSimple() throws Exception {
        try(ShmQueue matqueue = ShmQueue.createUsingMd5Hash("TEST");) {
            matqueue.create(100000, true);
            assertTrue(matqueue.isOpen());
            assertTrue(matqueue.isOwner());
            long errCode = -1;
            try {
                matqueue.open(false);
            } catch(final IpcException ipce) {
                errCode = ipce.nativeErrCode;
            }
            assertEquals(ALREADY_OPEN_ERROR_CODE, errCode);
            assertEquals(100000L, matqueue.getSize());
        }
    }

    @Test
    public void testSimpleReadWrite() throws Exception {
        try(final ShmQueue queue = ShmQueue.createUsingMd5Hash("TEST");) {
            queue.create(Long.BYTES, true);
            assertTrue(queue.tryAccess(bb -> bb.putLong(0x0123456789L)));
            assertTrue(queue.tryAccess(bb -> {
                assertEquals(0x0123456789L, bb.getLong());
            }));
        }
    }

    @Ignore // there currently is no actual locking
    @Test
    public void testBlocking() {
        try(final ShmQueue queue = ShmQueue.createUsingMd5Hash("TEST");) {
            queue.create(Long.BYTES, true);
            assertTrue(queue.tryAccess(bb -> {
                assertFalse(queue.tryLock());
                queue.unlock();
            }));

        }
    }

    @Test
    public void testPass() throws Exception {
        try(ShmQueue matqueue = ShmQueue.createUsingMd5Hash("TEST");
            final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {

            matqueue.create(mat.total() * mat.elemSize(), true);

            try(var shm = matqueue.tryAccessAsMat(0, mat.rows(), mat.cols(), mat.type());) {
                assertNotNull(shm);
                mat.copyTo(shm);
                shm.post();
            }

            assertTrue(matqueue.isMessageAvailable());

            try(var result = matqueue.tryAccessAsMat(0, mat.rows(), mat.cols(), mat.type());) {
                assertTrue(matqueue.isMessageAvailable());
                assertNotNull(result);
                assertTrue(mat.rasterOp(matRaster -> result.rasterOp(resRaster -> matRaster.equals(resRaster))));
                result.unpost();
            }

            assertFalse(matqueue.isMessageAvailable());
        }
    }

    public static final long NUM_MESSAGES = 50000;

    @Test
    public void testClientServerDuplex() throws Exception {
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

        final long fsize = size;
        final int ftype = type;
        final int frows = rows;
        final int fcols = cols;

        final AtomicBoolean serverException = new AtomicBoolean(false);

        final var server = new Thread(() -> {
            try(final ShmQueue matqueue = ShmQueue.createUsingMd5Hash("TEST");) {
                matqueue.create(fsize, true, 2);
                long startTime = 0;
                int count = 0;
                for(int i = 0; i < NUM_MESSAGES; i++) {
                    while(!matqueue.isMessageAvailable())
                        Thread.yield();
                    try(var m = matqueue.accessAsMat(0, frows, fcols, ftype);) {
                        assertTrue(matqueue.isMessageAvailable(0));
                        assertNotNull(m);
                        // copy the data
                        try(CvMat copy = new CvMat();) {
                            m.copyTo(copy);
                            m.unpost(0);
                        }
                    }
                    while(matqueue.isMessageAvailable(1)) // we want there to be a space for the message
                        Thread.yield();
                    try(var m = matqueue.accessAsMat(0, 1, 1, CvType.CV_64FC1);) {
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
            } catch(final RuntimeException rte) {
                rte.printStackTrace();
                serverException.set(true);
            }
        });
        server.start();

        try(final ShmQueue matqueue = ShmQueue.createUsingMd5Hash("TEST");
            final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {

            assertTrue(ConditionPoll.poll(o -> matqueue.open(false)));
            final long startTime = System.currentTimeMillis();
            int count = 0;
            for(int i = 0; i < NUM_MESSAGES; i++) {
                while(matqueue.isMessageAvailable(0)) // we want there to be a space for the message
                    Thread.yield();
                try(var m = matqueue.accessAsMat(0, mat.rows(), mat.cols(), mat.type());) {
                    mat.copyTo(m);
                    m.post(0);
                }
                // wait for response
                while(!matqueue.isMessageAvailable(1))
                    Thread.yield();
                try(var m = matqueue.accessAsMat(0, 1, 1, CvType.CV_64FC1);) {
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

        assertTrue(ConditionPoll.poll(server, t -> !t.isAlive()));
        assertFalse(serverException.get());
    }

    @Test
    public void testClientServer() throws Exception {
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

        final long fsize = size;
        final int ftype = type;
        final int frows = rows;
        final int fcols = cols;

        final AtomicBoolean serverException = new AtomicBoolean(false);

        final var server = new Thread(() -> {
            try(final ShmQueue matqueue = ShmQueue.createUsingMd5Hash("TEST");) {
                matqueue.create(fsize, true);
                long startTime = 0;
                int count = 0;
                for(int i = 0; i < NUM_MESSAGES; i++) {
                    while(!matqueue.isMessageAvailable())
                        Thread.yield();
                    try(var m = matqueue.accessAsMat(0, frows, fcols, ftype);) {
                        assertTrue(matqueue.isMessageAvailable());
                        assertNotNull(m);
                        // copy the data
                        try(CvMat copy = new CvMat();) {
                            m.copyTo(copy);
                            m.unpost();
                        }
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

        try(final ShmQueue matqueue = ShmQueue.createUsingMd5Hash("TEST");
            final Vfs vfs = new Vfs();
            final CvMat mat = ImageFile.readMatFromFile(vfs.toFile(new URI("classpath:///test-images/" + TEST_IMAGE)).getAbsolutePath());) {

            assertTrue(ConditionPoll.poll(o -> matqueue.open(false)));
            final long startTime = System.currentTimeMillis();
            int count = 0;
            for(int i = 0; i < NUM_MESSAGES; i++) {
                while(matqueue.isMessageAvailable()) // we want there to be a space for the message
                    Thread.yield();
                try(var m = matqueue.accessAsMat(0, mat.rows(), mat.cols(), mat.type());) {
                    mat.copyTo(m);
                    m.post();
                }
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
