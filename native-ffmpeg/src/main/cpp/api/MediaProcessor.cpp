/*
 * MediaProcessor.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "api/MediaProcessor.h"

#include <stdint.h>

#include "utils/log.h"
#include "common/kog_exports.h"

namespace pilecv4j {
namespace ffmpeg
{

#define COMPONENT "MPRC"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

static const AVRational UNKNOWN_TIME_BASE{0, 1};

// Pseudo-decoder name that triggers automatic NVDEC codec selection based on the
// stream's codec_id: h264→h264_cuvid, hevc→hevc_cuvid, av1→av1_cuvid, etc.
// Falls back to the software decoder when the cuvid variant is unavailable or
// when avcodec_open2 fails (no libnvcuvid, out of NVDEC surfaces, etc.).
static const char* const AUTO_NVDEC_DECODER = "auto-nvdec";

// Explicit opt-out: skip any hardware selection and use the default software decoder.
static const char* const SOFTWARE_DECODER = "software";

uint64_t MediaProcessor::open_codec(AVStream* pStream, AVDictionary** opts, AVCodecContext** codecCtxPtr, const char* decoderName) {
  PILECV4J_TRACE;

  *codecCtxPtr = nullptr;

  AVCodecParameters *pCodecParameters = pStream->codecpar;
  AVMediaType streamMediaType = pStream->codecpar->codec_type;
  AVCodec* pCodec = nullptr;

  // Track whether the selected codec is a hardware decoder so we know whether
  // to attempt a software fallback if avcodec_open2 later fails.
  bool isHardwareDecoder = false;

  {
    if (decoderName && strcmp(decoderName, SOFTWARE_DECODER) != 0) {
      if (isEnabled(TRACE))
        llog(TRACE, "Requested decoder '%s' for stream %d (media type %s)",
            decoderName, (int)pStream->index, av_get_media_type_string(streamMediaType));

      if (strcmp(decoderName, AUTO_NVDEC_DECODER) == 0) {
        // ── auto-nvdec: derive <codec>_cuvid from the stream's codec_id ──────
        const char* codecName = avcodec_get_name(pCodecParameters->codec_id);
        char cuvidName[64];
        snprintf(cuvidName, sizeof(cuvidName), "%s_cuvid", codecName);
        const AVCodec* tmpCodec = avcodec_find_decoder_by_name(cuvidName);
        if (tmpCodec && tmpCodec->type == streamMediaType) {
          pCodec = const_cast<AVCodec*>(tmpCodec);
          isHardwareDecoder = true;
          llog(INFO, "auto-nvdec: selected %s for stream codec %s", cuvidName, codecName);
        } else {
          llog(INFO, "auto-nvdec: no cuvid decoder for %s (tried %s), falling back to software",
              codecName, cuvidName);
          // pCodec stays null → software fallback below
        }
      } else {
        // ── named decoder (e.g. hevc_cuvid, h264_nvmpi) ──────────────────────
        const AVCodec* tmpCodec = avcodec_find_decoder_by_name(decoderName);
        const AVCodecDescriptor *desc;
        if (!tmpCodec && (desc = avcodec_descriptor_get_by_name(decoderName))) {
          tmpCodec = avcodec_find_decoder(desc->id);
          if (isEnabled(INFO) && tmpCodec)
            llog(INFO, "Matched decoder '%s' for codec '%s'.", tmpCodec->name, desc->name);
        }
        if (!tmpCodec) {
          llog(WARN, "Unknown decoder '%s'; falling back to software decoder\n", decoderName);
          // pCodec stays null → software fallback below
        } else if (tmpCodec->type != streamMediaType) {
          llog(INFO, "Specified decoder '%s' supporting %s doesn't apply to stream %d with media type %s; ignoring",
              decoderName, av_get_media_type_string(tmpCodec->type),
              (int)pStream->index, av_get_media_type_string(streamMediaType));
          // pCodec stays null → software fallback below
        } else {
          pCodec = const_cast<AVCodec*>(tmpCodec);
          // Heuristic: any explicitly named _cuvid / _nvmpi / _vaapi decoder is HW.
          isHardwareDecoder = (strstr(decoderName, "_cuvid")  != nullptr ||
                               strstr(decoderName, "_nvmpi")  != nullptr ||
                               strstr(decoderName, "_vaapi")  != nullptr ||
                               strstr(decoderName, "_videotoolbox") != nullptr);
        }
      }
    }

    if (!pCodec) {
      // Software fallback: use the default codec for this stream.
      const AVCodec* tmpCodec = avcodec_find_decoder(pCodecParameters->codec_id);
      if (tmpCodec == nullptr) {
        llog(ERROR, "Unsupported codec, ID %d", (int)pCodecParameters->codec_id);
        return MAKE_P_STAT(UNSUPPORTED_CODEC);
      }
      pCodec = const_cast<AVCodec*>(tmpCodec);
      isHardwareDecoder = false;
    }
  }

  // Helper lambda: allocate + fill a codec context for pCodec.
  // Returns the context in *codecCtxPtr; caller checks the return stat.
  auto openWithCodec = [&](AVCodec* codec) -> uint64_t {
    AVCodecContext* ctx = avcodec_alloc_context3(codec);
    *codecCtxPtr = ctx;
    if (!ctx) {
      llog(ERROR, "failed to allocate memory for AVCodecContext");
      return MAKE_P_STAT(FAILED_CREATE_CODEC_CONTEXT);
    }
    // https://stackoverflow.com/questions/40275242
    AVRational demuxTimeBase = pStream->time_base;
    if (!(demuxTimeBase.num == UNKNOWN_TIME_BASE.num && demuxTimeBase.den == UNKNOWN_TIME_BASE.den)) {
      llog(TRACE, "initializing decode codec context time_base to: %d/%d",
          (int)demuxTimeBase.num, (int)demuxTimeBase.den);
      ctx->time_base = demuxTimeBase;
    }
    uint64_t s = MAKE_AV_STAT(avcodec_parameters_to_context(ctx, pCodecParameters));
    if (isError(s)) return s;
    s = avcodec_open2(ctx, codec, opts);
    if (isEnabled(TRACE))
      llog(TRACE, "decode codec context time_base: %d/%d (after open)",
          ctx->time_base.num, ctx->time_base.den);
    return s;
  };

  uint64_t stat = openWithCodec(pCodec);

  // ── Hardware open failed: attempt software fallback ───────────────────────
  if (isError(stat) && isHardwareDecoder) {
    llog(WARN, "Hardware decoder '%s' failed to open (stat=0x%llx); retrying with software decoder",
        pCodec->name, (unsigned long long)stat);

    // Free the failed context before allocating a new one.
    if (*codecCtxPtr) {
      avcodec_free_context(codecCtxPtr);
      *codecCtxPtr = nullptr;
    }

    const AVCodec* swCodec = avcodec_find_decoder(pCodecParameters->codec_id);
    if (!swCodec) {
      llog(ERROR, "Software fallback: unsupported codec ID %d", (int)pCodecParameters->codec_id);
      return MAKE_P_STAT(UNSUPPORTED_CODEC);
    }
    llog(INFO, "Hardware fallback: opening software decoder '%s'", swCodec->name);
    stat = openWithCodec(const_cast<AVCodec*>(swCodec));
  }

  if (isError(stat)) {
    llog(ERROR, "failed to open codec '%s' through avcodec_open2", pCodec->name);
    return stat;
  }

  return stat;
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT void pcv4j_ffmpeg2_mediaProcessor_destroy(uint64_t uriSource) {
    if (isEnabled(TRACE))
      llog(TRACE, "destroying vid processor %" PRId64, uriSource);

    MediaProcessor* ret = (MediaProcessor*)uriSource;
    if (ret != nullptr) {
      ret->close();
      delete ret;
    }
  }

}

}
}