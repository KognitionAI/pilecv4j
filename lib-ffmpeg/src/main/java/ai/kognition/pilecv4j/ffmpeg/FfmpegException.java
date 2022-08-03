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

package ai.kognition.pilecv4j.ffmpeg;

public class FfmpegException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    public final long status;

    public FfmpegException(final String message) {
        super(message);
        this.status = 0;
    }

    public FfmpegException(final String message, final Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public FfmpegException(final long status, final String message) {
        super((status == 0) ? message : (sanitizeStatus(status) + ", " + message));
        this.status = status;
    }

    private static String sanitizeStatus(final long status) {
        if((status & 0xffffffff00000000L) == 0) { // not a pilecv4j status
            if((status & 0x0000000080000000L) != 0)
                return "AV status: " + (int)(status & ~0xffffffff00000000L);
            else
                return "AV status: " + (int)status;
        } else { // is a pilecv4j status
            return "Pilecv4j status: " + (int)(status >> 32);
        }
    }
}
