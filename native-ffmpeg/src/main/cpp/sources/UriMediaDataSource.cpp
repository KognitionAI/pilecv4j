/*
 * UriMediaDataSource.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "sources/UriMediaDataSource.h"

#include "common/kog_exports.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "UMDS"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

uint64_t UriMediaDataSource::open(AVFormatContext* preallocatedAvFormatCtx, AVDictionary** opts) {
  if (isEnabled(TRACE)) {
    llog(TRACE, "Opening stream for UriMediaDataSource %" PRId64 " with AVFormatContext %" PRId64 " and opts %" PRId64, (uint64_t)this, (uint64_t)preallocatedAvFormatCtx, (uint64_t)opts);
    llog(TRACE, "     fmt: %s", fmtNull ? "null" : fmt.c_str());
    llog(TRACE, "     uri: %s", uriNull ? "null" : uri.c_str());
  }

  const char* lfmt = fmtNull ? nullptr : fmt.c_str();
  AVInputFormat* ifmt = lfmt ? av_find_input_format(lfmt) : nullptr;

  if (lfmt && !ifmt) {
    llog(ERROR, "Failed to find the input format \"%s\"", lfmt);
    return MAKE_P_STAT(NO_INPUT_FORMAT);
  }

  // according to the docs for avformat_open_input, "a user-supplied AVFormatContext will be freed on failure."
  return MAKE_AV_STAT(avformat_open_input(&preallocatedAvFormatCtx, uriNull ? nullptr : uri.c_str(), ifmt, opts));
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_uriMediaDataSource_create(const char* uri) {
    UriMediaDataSource* ret = new UriMediaDataSource(uri);

    if (isEnabled(TRACE))
      llog(TRACE, "creating vid source %" PRId64, (uint64_t) ret);

    return (uint64_t)((MediaDataSource*)ret);
  }

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_uriMediaDataSource_create2(const char* fmt, const char* uri) {
    UriMediaDataSource* ret = new UriMediaDataSource(fmt, uri);

    if (isEnabled(TRACE))
      llog(TRACE, "creating vid source %" PRId64, (uint64_t) ret);

    return (uint64_t)((MediaDataSource*)ret);
  }
}

}
} /* namespace pilecv4j */
