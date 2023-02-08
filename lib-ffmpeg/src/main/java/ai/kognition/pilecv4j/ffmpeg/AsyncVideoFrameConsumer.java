package ai.kognition.pilecv4j.ffmpeg;

import static net.dempsy.util.Functional.ignore;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.ffmpeg.Ffmpeg.VideoFrameConsumer;
import ai.kognition.pilecv4j.image.VideoFrame;

public class AsyncVideoFrameConsumer implements VideoFrameConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncVideoFrameConsumer.class);
    private final AtomicReference<VideoFrame> ondeck = new AtomicReference<>(null);

    private final VideoFrameConsumer underlying;
    private final Thread thread;
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private static final AtomicLong threadCount = new AtomicLong(0);
    private static final String THREAD_NAME = "avp_";

    public AsyncVideoFrameConsumer(final VideoFrameConsumer underlying) {
        this.underlying = underlying;

        thread = start();
    }

    @Override
    public void handle(final VideoFrame frame) {
        try(VideoFrame next = frame.shallowCopy();
            VideoFrame prev = ondeck.getAndSet(next.returnMe());) {}
    }

    @Override
    public void close() {
        stop.set(true);
        thread.interrupt();
        underlying.close();
    }

    private Thread start() {
        final Thread ret = new Thread(() -> {
            while(!stop.get()) {
                boolean gotit = false;
                try(VideoFrame f = ondeck.getAndSet(null);) {
                    if(f != null) {
                        gotit = true;
                        underlying.handle(f);
                    }
                } catch(final RuntimeException rte) {
                    LOGGER.warn("Underlying video frame handler failed.", rte);
                }
                if(!gotit)
                    ignore(() -> Thread.sleep(1));
            }
        }, THREAD_NAME + threadCount.getAndIncrement());
        ret.start();
        return ret;
    }
}
