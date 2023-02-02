/*
 * UriMuxer.cpp
 *
 *  Created on: Jan 31, 2023
 *      Author: jim
 */

#include "UriMuxer.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

#define COMPONENT "UMUX"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

namespace pilecv4j
{
namespace ffmpeg
{

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

UriMuxer::~UriMuxer() {
  PILECV4J_TRACE;
  close();
}

}
} /* namespace pilecv4j */
