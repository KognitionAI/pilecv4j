/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#include <api/Muxer.h>
#include "processors/Remuxer.h"
#include "api/PacketSourceInfo.h"

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

Remuxer::~Remuxer() {}

uint64_t Remuxer::close() {
  PILECV4J_TRACE;
  return 0;
}

uint64_t Remuxer::setupStreams(AVCodecParameters** in_codecparpp) {
  PILECV4J_TRACE;
  int stream_index = 0;
  uint64_t iret = 0;

  if (!output)
    return MAKE_P_STAT(NO_OUTPUT);

  // open the muxer
  {
    AVDictionary* opts = nullptr;
    buildOptions(options, &opts);
    iret = output->open(&opts);
    if (opts != nullptr)
      av_dict_free(&opts);
    if (isError(iret)) {
      llog(ERROR, "Failed to openOutput: %ld", (long)iret);
      return iret;
    }
  }

  streams_list = new int[number_of_streams];

  if (!streams_list) {
    llog(ERROR, "Failed to allocate a simple array of ints.");
    iret = MAKE_AV_STAT(AVERROR(ENOMEM));
    goto fail;
  }

  stream_index = 0;
  for (unsigned int i = 0; i < number_of_streams; i++) {
    AVCodecParameters *in_codecpar = in_codecparpp[i];
    streams_list[i] = -1;
    if (!in_codecpar)
      continue;

    streams_list[i] = stream_index++;
    if (isError(iret = output->createNextStream(in_codecpar, nullptr))) {
      llog(ERROR, "Failed allocating output stream");
      goto fail;
    }
  }

  llog(TRACE, "Number of streams: %d", stream_index);

  if (isError(iret = output->ready())) {
    llog(ERROR, "Failed readying output");
    goto fail;
  }

  return 0;

  fail:
  if (streams_list != nullptr) {
    delete [] streams_list;
    streams_list = nullptr;
  }

  output->fail();

  return iret;
}

uint64_t Remuxer::setup(PacketSourceInfo* psi, const std::vector<std::tuple<std::string,std::string> >& poptions) {
  PILECV4J_TRACE;
  if (!psi)
    return MAKE_P_STAT(NO_PACKET_SOURCE_INFO);

  uint64_t iret = 0;

  // save off the options
  options = poptions;

  // set up the output streams
  if (isError(iret = psi->numStreams(&number_of_streams)))
    return iret;
  AVCodecParameters** in_codecparpp = new AVCodecParameters*[number_of_streams];
  streamTimeBases = new AVRational[number_of_streams];

  for (unsigned int i = 0; i < number_of_streams; i++) {
    in_codecparpp[i] = nullptr;

    AVStream* in_stream;
    if (isError(iret = psi->getStream(i, &in_stream)))
      return iret;

    streamTimeBases[i] = in_stream->time_base;
    AVCodecParameters *in_codecpar = in_stream->codecpar;

    // only video, audio, and subtitles will be remuxed.
    if (in_codecpar->codec_type != AVMEDIA_TYPE_AUDIO &&
        in_codecpar->codec_type != AVMEDIA_TYPE_VIDEO &&
        in_codecpar->codec_type != AVMEDIA_TYPE_SUBTITLE)
      continue;

    in_codecparpp[i] = avcodec_parameters_alloc();
    if (in_codecparpp[i] == nullptr) {
      llog(ERROR, "Failed to allocate a new AVCodecParameters");
      goto fail;
    }

    iret = MAKE_AV_STAT(avcodec_parameters_copy(in_codecparpp[i], in_codecpar));
    if (isError(iret)) {
      llog(ERROR, "Failed to copy codec parameters");
      goto fail;
    }

    {
      // see: https://stackoverflow.com/questions/33981707/ffmpeg-copy-streams-without-transcode
      // I don't fully understand this but if I can access the information then I'll try.
      unsigned int tag = 0;
      bool gotInputTag = true;
      if (isError(psi->getCodecTag(in_codecpar->codec_id, &tag))) {
        llog(DEBUG, "Failed to get tag");
        gotInputTag = false;
      }
      if (gotInputTag)
        in_codecparpp[i]->codec_tag = tag;
    }
  }

  iret = setupStreams(in_codecparpp);
  if (isError(iret))
    goto fail;

  alreadySetup = true;
  return 0;

  fail:
  if (in_codecparpp) {
    for (int i = 0; i < number_of_streams; i++) {
      if (in_codecparpp[i])
        avcodec_parameters_free(&(in_codecparpp[i]));
    }
    delete [] in_codecparpp;
    in_codecparpp = nullptr;
  }

  if (streamTimeBases) {
    delete [] streamTimeBases;
    streamTimeBases = nullptr;
  }

  return iret;
}

uint64_t Remuxer::preFirstFrame() {
  PILECV4J_TRACE;
  startTime = now();
  return 0;
}

uint64_t Remuxer::remuxPacket(const AVPacket * inPacket) {
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

  AVRational& time_base = streamTimeBases[input_stream_index];

  //AVStream * in_stream = formatCtx->streams[input_stream_index];
  //AVRational& time_base = in_stream->time_base;

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

uint64_t Remuxer::handlePacket(AVPacket* pPacket, AVMediaType streamMediaType) {
  PILECV4J_TRACE;
  uint64_t ret = remuxPacket(pPacket);
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

KAI_EXPORT uint64_t pcv4j_ffmpeg2_remuxer_create(uint64_t outputRef, int32_t maxRemuxErrorCount) {
  PILECV4J_TRACE;
  Muxer* output = (Muxer*)outputRef;
  if (!output) {
    llog(ERROR, "No output specified for remuxer");
    return MAKE_P_STAT(NO_OUTPUT);
  }
  MediaProcessor* ret = new Remuxer(output, maxRemuxErrorCount);
  return (uint64_t)ret;
}

}

}
} /* namespace pilecv4j */

