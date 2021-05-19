#include <stdint.h>

extern "C"
{
#include "libavformat/avformat.h"
}

/**
 * This file contains a few methods callable from java that return common codes
 */

extern "C" {
  int32_t pcv4j_ffmpeg_code_averror_eof() {
    return AVERROR_EOF;
  }

  int32_t pcv4j_ffmpeg_code_seek_set() {
    return SEEK_SET;
  }

  int32_t pcv4j_ffmpeg_code_seek_cur() {
    return SEEK_CUR;
  }

  int32_t pcv4j_ffmpeg_code_seek_end() {
    return SEEK_END;
  }

  int32_t pcv4j_ffmpeg_code_seek_size() {
    return AVSEEK_SIZE;
  }

  int32_t pcv4j_ffmpeg_code_eagain() {
    return AVERROR(EAGAIN);
  }
}

