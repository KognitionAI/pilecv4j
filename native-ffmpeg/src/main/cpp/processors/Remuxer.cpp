/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#include "processors/Remuxer.h"
#include "api/MediaOutput.h"

#include "utils/log.h"

#include "common/kog_exports.h"

extern "C" {
#include <libswscale/swscale.h>
}

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "RMUX"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

Remuxer::~Remuxer() {
}

uint64_t Remuxer::close() {
  PILECV4J_TRACE;
  if (output)
    return output->close();
  return 0;
}

uint64_t Remuxer::setup(AVFormatContext* input_format_context, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams) {
  PILECV4J_TRACE;
  // ==================================================
  // Create and setup the output_format_context if we're remuxing
  // ==================================================

  if (!output)
    return MAKE_P_STAT(NO_OUTPUT);

  int ret = 0;
  uint64_t iret = 0;
  int number_of_streams = -1;
  int stream_index = 0;

  AVFormatContext* output_format_context = nullptr;
  iret = output->allocateOutputContext(&output_format_context);
  if (!output_format_context || isError(iret))
    return iret;

  if (ret < 0) {
    llog(ERROR, "Failed to allocateOutputContext: %ld", (long)iret);
    goto fail;
  }

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

  {
    AVDictionary* opts = nullptr;
    buildOptions(options, &opts);
    iret = output->openOutput(&opts);
    if (opts != nullptr)
      av_dict_free(&opts);
  }

  if (isError(iret)) {
    llog(ERROR, "Failed to openOutput: %ld", (long)iret);
    goto fail;
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
  if (output_format_context != nullptr)
    output->fail();
  return isError(iret) ? iret : MAKE_AV_STAT(ret);
}

uint64_t Remuxer::preFirstFrame(AVFormatContext* avformatCtx) {
  startTime = now();
  return 0;
}

uint64_t Remuxer::remuxPacket(AVFormatContext* formatCtx, const AVPacket * inPacket) {
  PILECV4J_TRACE;

  if (!output) {
    llog(ERROR, "Can't remux packet without a destination (output) set");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  AVFormatContext* output_format_context = output->getFormatContext();
  if (!output_format_context) {
    llog(ERROR, "Can't remux packet without an output AVFormatContext");
    return MAKE_P_STAT(NO_OUTPUT);
  }

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
  if (pPacket->pts == AV_NOPTS_VALUE) {
    pPacket->pts = av_rescale_q((int64_t)(now() - startTime), millisecondTimeBase, time_base);
    pPacket->dts = pPacket->pts;
  }

  AVStream* out_stream = output_format_context->streams[0];
  pPacket->stream_index = streams_list[input_stream_index];
  pPacket->pts = av_rescale_q_rnd(pPacket->pts, time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
  pPacket->dts = av_rescale_q_rnd(pPacket->dts, time_base, out_stream->time_base, (AVRounding)(AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX));
  pPacket->duration = av_rescale_q(pPacket->duration, time_base, out_stream->time_base);
  // https://ffmpeg.org/doxygen/trunk/structAVPacket.html#ab5793d8195cf4789dfb3913b7a693903
  pPacket->pos = -1;
  if (isEnabled(TRACE))
    logPacket(TRACE, COMPONENT, "Rescaled Packet", pPacket, output_format_context);

  int ret = av_interleaved_write_frame(output_format_context, pPacket);
  av_packet_free(&pPacket);
  if (ret < 0)
    llog(ERROR, "Error muxing packet \"%s\"", av_err2str(ret));
  return MAKE_AV_STAT(ret);
}

uint64_t Remuxer::handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType) {
  PILECV4J_TRACE;
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

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_remuxer_create(int32_t maxRemuxErrorCount) {
  PILECV4J_TRACE;
  MediaProcessor* ret = new Remuxer(maxRemuxErrorCount);
  return (uint64_t)ret;
}

KAI_EXPORT void pcv4j_ffmpeg2_remuxer_setOutput(uint64_t remuxRef, uint64_t outputRef) {
  PILECV4J_TRACE;
  Remuxer* ths = (Remuxer*)remuxRef;
  MediaOutput* output = (MediaOutput*)outputRef;
  ths->setMediaOutput(output);
}

}

}
} /* namespace pilecv4j */

