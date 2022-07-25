/*
 * FirstVideoStreamSelector.cpp
 *
 *  Created on: Jul 13, 2022
 *      Author: jim
 */

#include "selectors/FirstVideoStreamSelector.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

#include "common/kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "FVSS"

inline static void log(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

static uint64_t findFirstSupportedVidCodec(AVFormatContext* pFormatContext, AVCodecParameters** pCodecParameters, int* rvsi) {

  // if there's no streams, there's no stream.
  if (pFormatContext->streams == nullptr)
    return MAKE_P_STAT(NO_STREAM);

  int video_stream_index = -1;
  bool foundUnsupportedCode = false;

  // loop though all the streams and print its main information.
  // when we find the first video stream, record the information
  // from it.
  for (unsigned int i = 0; i < pFormatContext->nb_streams; i++)
  {
    AVStream* lStream = pFormatContext->streams[i];

    // minimally validate the stream
    if (lStream == nullptr) {
      log(WARN, "AVStream is missing from stream array [%d]", i);
      continue;
    }

    AVCodecParameters *pLocalCodecParameters = lStream->codecpar;
    if (isEnabled(DEBUG)) {
      log(DEBUG, "AVStream->time_base before open codec %d/%d", lStream->time_base.num, lStream->time_base.den);
      log(DEBUG, "AVStream->r_frame_rate before open codec %d/%d", lStream->r_frame_rate.num, lStream->r_frame_rate.den);
      log(DEBUG, "AVStream->avg_frame_rate before open codec %d/%d", lStream->avg_frame_rate.num, lStream->avg_frame_rate.den);
      log(DEBUG, "AVStream->start_time %" PRId64, lStream->start_time);
      log(DEBUG, "AVStream->duration %" PRId64, lStream->duration);

      log(INFO, "finding the proper decoder (CODEC)");
    }

    if (!decoderExists(pLocalCodecParameters->codec_id))
      continue;

    // when the stream is a video we store its index, codec parameters and codec, etc.
    if (pLocalCodecParameters->codec_type == AVMEDIA_TYPE_VIDEO) {
      if (video_stream_index == -1) {
        video_stream_index = i;
        *pCodecParameters = pLocalCodecParameters;
      }

      log(DEBUG, "Video Codec (%d): resolution %d x %d",pLocalCodecParameters->codec_id, pLocalCodecParameters->width, pLocalCodecParameters->height);
    } else if (pLocalCodecParameters->codec_type == AVMEDIA_TYPE_AUDIO) {
      log(DEBUG, "Audio Codec: %d channels, sample rate %d", pLocalCodecParameters->channels, pLocalCodecParameters->sample_rate);
    }

    // print its name, id and bitrate
    log(INFO, "\tCodec ID %d bit_rate %lld", pLocalCodecParameters->codec_id, pLocalCodecParameters->bit_rate);
  }

  if (video_stream_index == -1) {
    if (foundUnsupportedCode) {
      return MAKE_P_STAT(NO_SUPPORTED_CODEC);
    } else {
      return MAKE_P_STAT(NO_STREAM);
    }
  }

  *rvsi = video_stream_index;

  return 0;
}

uint64_t FirstVideoStreamSelector::selectStreams(AVFormatContext* formatCtx, bool* useStreams, int32_t numStreams) {

  // this component describes the properties of a codec used by the stream
  // https://ffmpeg.org/doxygen/trunk/structAVCodecParameters.html
  AVCodecParameters *pCodecParameters = nullptr;
  int video_stream_index = -1;

  uint64_t stat = findFirstSupportedVidCodec(formatCtx, &pCodecParameters, &video_stream_index);
  if (isError(stat))
    return stat;

  int32_t streamIndex = video_stream_index;

  for (int32_t i = 0; i < numStreams; i++) {
    useStreams[(int)i] = false;
    if (i == streamIndex)
      useStreams[(int)i] = true;
  }

  return 0;
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_firstVideoStreamSelector_create() {
    FirstVideoStreamSelector* ret = new FirstVideoStreamSelector();

    if (isEnabled(TRACE))
      log(TRACE, "creating vid source %" PRId64, (uint64_t) ret);

    return (uint64_t)((StreamSelector*)ret);
  }
}

}
} /* namespace pilecv4j */


