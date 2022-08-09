/*
 * MediaOutput.cpp
 *
 *  Created on: Aug 9, 2022
 *      Author: jim
 */

#include "api/MediaOutput.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"
#include "common/kog_exports.h"

namespace pilecv4j
{
namespace ffmpeg
{
#define COMPONENT "MOUT"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

uint64_t MediaOutput::close() {
  PILECV4J_TRACE;
  if (!closed) {
    closed = true;
  }
  return 0; // TODO: Remuxer::close();
}

void MediaOutput::fail() {
  PILECV4J_TRACE;
}

MediaOutput::~MediaOutput()
{
  PILECV4J_TRACE;
  if (!isClosed())
    llog(ERROR, "Deleting MediaOutput prior to closing!");
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

KAI_EXPORT void pcv4j_ffmpeg2_mediaOutput_delete(uint64_t outputRef) {
  PILECV4J_TRACE;
  if (outputRef) {
    MediaOutput* output = (MediaOutput*)outputRef;
    output->close();
    delete output;
  }
}

}

}

} /* namespace pilecv4j */
