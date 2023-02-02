/*
 * JavaStreamSelector.cpp
 *
 *  Created on: Jul 17, 2022
 *      Author: jim
 */

#include "filters/JavaStreamSelector.h"
#include "api/PacketSourceInfo.h"

#include "utils/log.h"
#include "utils/pilecv4j_ffmpeg_utils.h"

#include "common/kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "JASS"

inline static void log(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

uint64_t JavaStreamSelector::setup(PacketSourceInfo* mediaSource, const std::vector<std::tuple<std::string,std::string> >& options) {

  uint64_t result = 0;
  if (isError(result = mediaSource->numStreams(&numStreams)))
    return result;

  int32_t* selected = new int32_t[numStreams];

  for (int i = 0; i < numStreams; i++)
    selected[i] = 1; // defaulted to selected

  uint32_t rc = (*callback)((int32_t)numStreams, selected);

  if (!rc) {
    result = MAKE_P_STAT(STREAM_SELECT_FAILED);
    goto end;
  }

  useStreams = new bool[numStreams];
  for (int i = 0; i < numStreams; i++)
    useStreams[i] = selected[i] ? true : false;

end:
  delete [] selected;
  return result;
}

bool JavaStreamSelector::filter(AVPacket* pPacket, AVMediaType streamMediaType) {
  const int stream_index = pPacket->stream_index;
  if (stream_index >= numStreams)
    return false;
  return useStreams[stream_index];
}

extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_javaStreamSelector_create(select_streams callback) {

  uint64_t ret = (uint64_t) ((PacketFilter*)new JavaStreamSelector(callback));
  if (isEnabled(TRACE))
    log(TRACE, "Creating new JavaStreamSelector: %" PRId64, ret);
  return ret;
}

}
} /* namespace pilecv4j */

}
