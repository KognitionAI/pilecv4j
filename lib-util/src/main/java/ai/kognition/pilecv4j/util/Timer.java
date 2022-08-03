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

package ai.kognition.pilecv4j.util;

public final class Timer {
    private long startTime;
    private long endTime;

    public static final long nanoSecondsPerSecond = 1000000000L;
    public static final double secondsPerNanosecond = 1.0D / nanoSecondsPerSecond;

    public final void start() {
        startTime = System.nanoTime();
    }

    public final String stop() {
        endTime = System.nanoTime();
        return toString();
    }

    public final float getSeconds() {
        return (float)((endTime - startTime) * secondsPerNanosecond);
    }

    // public final int getTenthsOfSeconds()
    // {
    // return (int)(((double)(((endTime - startTime) % 1000)) / 100) + 0.5);
    // }

    @Override
    public final String toString() {
        return String.format("%.3f", getSeconds());
    }
}
