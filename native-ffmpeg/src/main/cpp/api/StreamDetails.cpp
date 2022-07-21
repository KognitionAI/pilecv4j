
#include "api/StreamDetails.h"

namespace pilecv4j
{
namespace ffmpeg
{

uint64_t StreamDetails::fillStreamDetails(AVFormatContext* formatCtx, StreamDetails** ppdetails, int* nb) {

  const int numStreams = formatCtx->nb_streams;
  *nb = numStreams;

  if (numStreams > 0) {
    *ppdetails = new StreamDetails[numStreams];

    for (int i = 0; i < numStreams; i++) {
      StreamDetails& details = (*ppdetails)[i];

      const AVStream* stream = formatCtx->streams ? formatCtx->streams[i] : nullptr;
      const AVCodecParameters *pLocalCodecParameters = stream == nullptr ? nullptr : stream->codecpar;
      if (pLocalCodecParameters) {
        details.stream_index = stream->index;
        details.mediaType = pLocalCodecParameters ? pLocalCodecParameters->codec_type : AVMEDIA_TYPE_UNKNOWN;

        details.fps_num = stream->avg_frame_rate.num;
        details.fps_den = stream->avg_frame_rate.den;

        details.tb_num = stream->time_base.num;
        details.tb_den = stream->time_base.den;

        details.codec_id = pLocalCodecParameters->codec_id;

        const AVCodecDescriptor* cd = avcodec_descriptor_get(pLocalCodecParameters->codec_id);
        if (cd)
          details.setCodecName(cd->name);
      }
    }
  } else
    *ppdetails = nullptr;


  return 0;
}

}
} /* namespace pilecv4j */
