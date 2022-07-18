/*
 * UriMediaDataSource.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "sources/UriMediaDataSource.h"

#include "kog_exports.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

namespace pilecv4j
{

#define COMPONENT "UMDS"

inline static void log(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

uint64_t UriMediaDataSource::open(AVFormatContext* preallocatedAvFormatCtx, AVDictionary** opts) {
  if (isEnabled(TRACE))
    log(TRACE, "Opening stream for UriMediaDataSource %" PRId64 " with AVFormatContext %" PRId64 " and opts %" PRId64, (uint64_t)this, (uint64_t)preallocatedAvFormatCtx, (uint64_t)opts);

  // according to the docs for avformat_open_input, "a user-supplied AVFormatContext will be freed on failure."
  return MAKE_AV_STAT(avformat_open_input(&preallocatedAvFormatCtx, uri.c_str(), nullptr, opts));
}


} /* namespace pilecv4j */


//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_uriMediaDataSource_create(const char* uri) {
    pilecv4j::UriMediaDataSource* ret = new pilecv4j::UriMediaDataSource(uri);

    if (pilecv4j::isEnabled(pilecv4j::TRACE))
      pilecv4j::log(pilecv4j::TRACE, "creating vid source %" PRId64, (uint64_t) ret);

    return (uint64_t)((pilecv4j::MediaDataSource*)ret);
  }
}
