#include <thread>
#include <stdio.h>
#include <string>
#include "log.h"

static const char* logLevelNames[] = {
    "TRACE",
    "DEBUG",
    "INFO",
    "WARN",
    "ERROR",
    "FATAL",
    nullptr
};
// =====================================================

namespace pilecv4j {
namespace python {
  LogLevel logLevel = INFO;

#ifdef LOGGING
  void setLogLevel(LogLevel ll) {
    logLevel = ll;
  }
  void log(LogLevel llevel, const char *fmt, ...)
  {
    if (logLevel <= llevel) {
      size_t tid = std::hash<std::thread::id>{}(std::this_thread::get_id());
      std::string tidstr = std::to_string(tid);
      va_list args;
      fputs( "python: [", stderr );
      fputs( tidstr.c_str(), stderr);
      fputs( "] [", stderr);
      fputs( logLevelNames[llevel], stderr );
      fputs( "] ", stderr);
      va_start( args, fmt );
      vfprintf( stderr, fmt, args );
      va_end( args );
      fputs( "\n", stderr );
      fflush(stderr);
    }
  }
#endif
}
}


