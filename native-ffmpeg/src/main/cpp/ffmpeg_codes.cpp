#include <stdint.h>

#include "kog_exports.h"

extern "C"
{
#include "libavformat/avformat.h"
}

/**
 * This file contains a few methods callable from java that return common codes
 */

extern "C" {
  KAI_EXPORT int32_t pcv4j_ffmpeg_code_averror_eof() {
    return AVERROR_EOF;
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg_code_seek_set() {
    return SEEK_SET;
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg_code_seek_cur() {
    return SEEK_CUR;
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg_code_seek_end() {
    return SEEK_END;
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg_code_seek_size() {
    return AVSEEK_SIZE;
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg_code_eagain() {
    return AVERROR(EAGAIN);
  }
}

