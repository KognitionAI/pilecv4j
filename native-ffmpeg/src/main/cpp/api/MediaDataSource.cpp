/*
 * MediaDataSource.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "api/MediaDataSource.h"

#include "kog_exports.h"
#include "utils/log.h"

namespace pilecv4j {
namespace ffmpeg
{

#define COMPONENT "MDSR"

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}
//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT void pcv4j_ffmpeg2_mediaDataSource_destroy(uint64_t uriSource) {
    if (isEnabled(TRACE))
      llog(TRACE, "destroying vid source %" PRId64, uriSource);

    MediaDataSource* ret = (MediaDataSource*)uriSource;
    if (ret != nullptr)
      delete ret;
  }
}
}
}


