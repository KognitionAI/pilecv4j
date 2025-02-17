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
