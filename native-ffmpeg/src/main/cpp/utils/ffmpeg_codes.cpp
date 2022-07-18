#include <stdint.h>

#include "kog_exports.h"

extern "C"
{
#include "libavformat/avformat.h"
}

#include "utils/pilecv4j_ffmpeg_utils.h"
/**
 * This file contains a few methods callable from java that return common codes
 */

extern "C" {
KAI_EXPORT uint64_t pcv4j_ffmpeg_code_averror_eof_as_kognition_stat() {
  return MAKE_AV_STAT(AVERROR_EOF);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg_code_averror_unknown_as_kognition_stat() {
  return MAKE_AV_STAT(AVERROR_UNKNOWN);
}

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

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_UNKNOWN() {
    return AVMEDIA_TYPE_UNKNOWN;
  }

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_VIDEO() {
    return AVMEDIA_TYPE_VIDEO;
  }

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_AUDIO() {
    return AVMEDIA_TYPE_AUDIO;
  }

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_DATA() {
    return AVMEDIA_TYPE_DATA;
  }

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_SUBTITLE() {
    return AVMEDIA_TYPE_SUBTITLE;
  }

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_ATTACHMENT() {
    return AVMEDIA_TYPE_ATTACHMENT;
  }

  KAI_EXPORT int32_t  pcv4j_ffmpeg2_mediaType_NB() {
    return AVMEDIA_TYPE_NB;
  }
}

