
#include "api/StreamSelector.h"

#include "kog_exports.h"
#include "utils/log.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "STRS"

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

  KAI_EXPORT void pcv4j_ffmpeg2_streamSelector_destroy(uint64_t ssRef) {
    if (isEnabled(TRACE))
      llog(TRACE, "destroying stream selector %" PRId64, ssRef);

    StreamSelector* ret = (StreamSelector*)ssRef;
    if (ret != nullptr)
      delete ret;
  }

}
}

} /* namespace pilecv4j */
