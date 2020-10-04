package ai.kognition.pilecv4j.tracking;

import java.util.function.Supplier;

import ai.kognition.pilecv4j.tracking.tracker.TrackerCSRT;
import ai.kognition.pilecv4j.tracking.tracker.TrackerKCF;
import ai.kognition.pilecv4j.tracking.tracker.TrackerMOSSE;

public enum TrackerImpl implements Supplier<Tracker> {
    CSRT {
        @Override
        public TrackerCSRT get() {
            return new TrackerCSRT();
        }

        @Override
        public boolean supportsMasking() {
            return TrackerCSRT.SUPPORTS_MASKING;
        }
    },
    KCF {
        @Override
        public TrackerKCF get() {
            return new TrackerKCF();
        }

        @Override
        public boolean supportsMasking() {
            return TrackerKCF.SUPPORTS_MASKING;
        }
    },
    MOSSE {
        @Override
        public TrackerMOSSE get() {
            return new TrackerMOSSE();
        }

        @Override
        public boolean supportsMasking() {
            return TrackerMOSSE.SUPPORTS_MASKING;
        }
    };

    @Override
    public abstract Tracker get();

    public abstract boolean supportsMasking();
}
