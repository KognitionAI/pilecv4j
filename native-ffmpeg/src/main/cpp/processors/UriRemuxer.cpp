/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#include "processors/UriRemuxer.h"

#include "utils/log.h"

#include "kog_exports.h"

extern "C" {
#include <libswscale/swscale.h>
}

namespace pilecv4j
{
#define COMPONENT "URIR"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

UriRemuxer::~UriRemuxer() {
  if (output_format_context) {
    //https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga7f14007e7dc8f481f054b21614dfec13
    av_write_trailer(output_format_context);

    if (cleanupIoContext) {
      avio_closep(&output_format_context->pb);
      output_format_context->pb = nullptr;
    }
    avformat_free_context(output_format_context);
  }
}

uint64_t UriRemuxer::setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams) {
  // ==================================================
  // Create and setup the output_format_context if we're remuxing
  // ==================================================

  return setupRemux(avformatCtx);
  // ==================================================
}

// sets output_format_context, cleanupIoContext, and streams_list
uint64_t UriRemuxer::setupRemux(AVFormatContext* input_format_context) {
  int ret = 0;
  output_format_context = nullptr;
  const char* loutputUri = outputUri.c_str();
  int number_of_streams = -1;
  int stream_index = 0;

  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  llog(DEBUG, "UriRemuxer: [%s, %s]", PO(lfmt), PO(loutputUri));

  ret = avformat_alloc_output_context2(&output_format_context, nullptr, lfmt, loutputUri);
  if (!output_format_context) {
    llog(ERROR, "Failed to allocate output format context using a format of \"%s\" and an output file of \"%s\"",
        lfmt == nullptr ? "[NULL]" : lfmt, loutputUri);
    return ret < 0 ? MAKE_AV_STAT(ret) : MAKE_AV_STAT(AVERROR_UNKNOWN);
  }

  if (ret < 0)
    goto fail;

  number_of_streams = input_format_context->nb_streams;
  streams_list = new int[number_of_streams];

  if (!streams_list) {
    llog(ERROR, "Failed to allocate a simple array of ints.");
    return MAKE_AV_STAT(AVERROR(ENOMEM));
  }

  stream_index = 0;
  for (unsigned int i = 0; i < input_format_context->nb_streams; i++) {
    AVStream* in_stream = input_format_context->streams[i];
    AVCodecParameters *in_codecpar = in_stream->codecpar;

    // only video, audio, and subtitles will be remuxed.
    if (in_codecpar->codec_type != AVMEDIA_TYPE_AUDIO &&
        in_codecpar->codec_type != AVMEDIA_TYPE_VIDEO &&
        in_codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE) {
      streams_list[i] = -1;
      continue;
    }
    streams_list[i] = stream_index++;
    AVStream* out_stream = avformat_new_stream(output_format_context, NULL);
    if (!out_stream) {
      llog(ERROR, "Failed allocating output stream");
      delete [] streams_list;
      return MAKE_AV_STAT(AVERROR_UNKNOWN);
    }
    ret = avcodec_parameters_copy(out_stream->codecpar, in_codecpar);
    if (ret < 0) {
      llog(ERROR, "Failed to copy codec parameters");
      goto fail;
    }

    {
      // set the tag ....
      bool setTag = true;
      unsigned int tag = 0;
      if (av_codec_get_tag2(input_format_context->iformat->codec_tag, in_codecpar->codec_id, &tag) < 0) {
        llog(DEBUG, "Failed to get tag");
        setTag = false;
      }
      if (setTag)
        out_stream->codecpar->codec_tag = tag;
    }
  }

  // https://ffmpeg.org/doxygen/trunk/group__lavf__misc.html#gae2645941f2dc779c307eb6314fd39f10
  av_dump_format(output_format_context, 0, loutputUri, 1);

  // unless it's a no file (we'll talk later about that) write to the disk (FLAG_WRITE)
  // but basically it's a way to save the file to a buffer so you can store it
  // wherever you want.
  if (!(output_format_context->oformat->flags & AVFMT_NOFILE)) {
    ret = avio_open(&output_format_context->pb, loutputUri, AVIO_FLAG_WRITE);
    if (ret < 0) {
      llog(ERROR, "Could not open output file '%s'", loutputUri);
      goto fail;
    }
    cleanupIoContext = true; // we need to close what we opened.
  }

  // https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga18b7b10bb5b94c4842de18166bc677cb
  ret = avformat_write_header(output_format_context, nullptr);
  if (ret < 0) {
    llog(ERROR, "Error occurred when opening output file\n");
    goto fail;
  }

  return 0;

  fail:
  if (streams_list != nullptr)
    delete [] streams_list;
  if (output_format_context != nullptr) {
    if (cleanupIoContext) {
      avio_closep(&output_format_context->pb);
      output_format_context->pb = nullptr;
    }
    cleanupIoContext = false;
    avformat_free_context(output_format_context);
    output_format_context = nullptr;
  }
  return MAKE_AV_STAT(ret);
}

uint64_t UriRemuxer::preFirstFrame(AVFormatContext* avformatCtx) {
  startTime = now();
  return 0;
}

uint64_t UriRemuxer::remuxPacket(AVFormatContext* formatCtx, const AVPacket * inPacket) {

  int input_stream_index = inPacket->stream_index;
  if (streams_list[input_stream_index] < 0)
    return 0;

  AVStream * in_stream = formatCtx->streams[input_stream_index];
  AVRational& time_base = in_stream->time_base;

  AVPacket* pPacket = av_packet_clone(inPacket);
  if (!pPacket) {
    llog(ERROR, "Failed to clone a packet");
    return MAKE_AV_STAT(AVERROR_UNKNOWN);
  }
  // if the input stream has no valid pts (like when reading the live feed from milestone)
  // then we're going to calculate it.
  llog(TRACE, "        ->pPacket,ofc %" PRId64 ", %d / %d", pPacket, time_base.num, time_base.den);
  if (pPacket->pts == AV_NOPTS_VALUE) {
    pPacket->pts = av_rescale_q((int64_t)(now() - startTime), millisecondTimeBase, time_base);
    pPacket->dts = pPacket->pts;

    llog(TRACE, "calced  ->pts,dts %" PRId64 ",%" PRId64, pPacket->pts, pPacket->dts);
  }

  AVStream* out_stream = output_format_context->streams[0];
  pPacket->stream_index = streams_list[input_stream_index];
  pPacket->pts = av_rescale_q_rnd(pPacket->pts, time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
  pPacket->dts = av_rescale_q_rnd(pPacket->dts, time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
  pPacket->duration = av_rescale_q(pPacket->duration, time_base, out_stream->time_base);
  // https://ffmpeg.org/doxygen/trunk/structAVPacket.html#ab5793d8195cf4789dfb3913b7a693903
  pPacket->pos = -1;
  if (isEnabled(TRACE))
    llog(TRACE, "rescaled  pts,dts %" PRId64 ",%" PRId64 ", outstream time_base: %d/%d",
        pPacket->pts, pPacket->dts, out_stream->time_base.num, out_stream->time_base.den);

  int ret = av_interleaved_write_frame(output_format_context, pPacket);
  av_packet_free(&pPacket);
  if (ret < 0)
    llog(ERROR, "Error muxing packet \"%s\"", av_err2str(ret));
  return MAKE_AV_STAT(ret);
}

uint64_t UriRemuxer::handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType) {
  uint64_t ret = remuxPacket(avformatCtx,pPacket);
  if (ret != 0) {
    remuxErrorCount++;
    if (remuxErrorCount > maxRemuxErrorCount) {
      llog(ERROR, "TOO MANY CONTINUOUS REMUX ERRORS(%d). EXITING!", (int)remuxErrorCount);
      return ret;
    } else {
      return 0; // we'll try again
    }
  } else
    remuxErrorCount = 0;
  return ret;
}

} /* namespace pilecv4j */

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_uriRemuxer_create(const char* pfmt, const char* poutputUri, int32_t maxRemuxErrorCount) {
    pilecv4j::MediaProcessor* ret = new pilecv4j::UriRemuxer(pfmt, poutputUri, maxRemuxErrorCount);
    return (uint64_t)ret;
  }

}

