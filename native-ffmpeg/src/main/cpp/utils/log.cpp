#include <thread>
#include <stdio.h>
#include <string>

#include "utils/log.h"

#include "kog_exports.h"

extern "C" {
#include <libavutil/log.h>
}


// currently (04/28/2022) LOGGING is defined by default in the CMakeLists.txt file.
#ifdef LOGGING
static const char* logLevelNames[] = {
    "TRACE",
    "DEBUG",
    "INFO",
    "WARN",
    "ERROR",
    "FATAL",
    nullptr
};

static int avLogLevelLookup[] = {
    AV_LOG_TRACE,
    AV_LOG_DEBUG,
    AV_LOG_INFO,
    AV_LOG_WARNING,
    AV_LOG_ERROR,
    AV_LOG_FATAL,
};
// =====================================================

namespace pilecv4j {
  static LogLevel logLevel = INFO;
}

bool pilecv4j::isEnabled(LogLevel llevel) {
  return logLevel <= llevel;
}

void pilecv4j::setLogLevel(LogLevel ll) {
  logLevel = ll;

  av_log_set_level(avLogLevelLookup[ll]);
}

void pilecv4j::log(LogLevel llevel, const char* component, const char* fmt, va_list args) {
  if (logLevel <= llevel) {
    char hex_string[1024];
    size_t tid = std::hash<std::thread::id>{}(std::this_thread::get_id());
    //std::string tidstr = std::to_string(tid);
    sprintf(hex_string, "%lx", (unsigned long)tid);
    fputs(component, stdout);
    fputs( ": [", stdout );
    //fputs( tidstr.c_str(), stdout);
    fputs(hex_string, stdout);
    fputs( "] [", stdout);
    fputs( logLevelNames[llevel], stdout );
    fputs( "] ", stdout);
    vfprintf( stdout, fmt, args );
    fputs( "\n", stdout );
  }
}

void pilecv4j::log(LogLevel llevel, const char* component, const char *fmt, ...)
{
  if (logLevel <= llevel) {
    char hex_string[1024];
    size_t tid = std::hash<std::thread::id>{}(std::this_thread::get_id());
    //std::string tidstr = std::to_string(tid);
    sprintf(hex_string, "%lx", (unsigned long)tid);
    va_list args;
    fputs(component, stdout);
    fputs( ": [", stdout );
    //fputs( tidstr.c_str(), stdout);
    fputs(hex_string, stdout);
    fputs( "] [", stdout);
    fputs( logLevelNames[llevel], stdout );
    fputs( "] ", stdout);
    va_start( args, fmt );
    vfprintf( stdout, fmt, args );
    va_end( args );
    fputs( "\n", stdout );
  }
}

extern "C" {

KAI_EXPORT void pcv4j_ffmpeg2_logging_setLogLevel(int32_t plogLevel) {
  pilecv4j::setLogLevel(static_cast<pilecv4j::LogLevel>(plogLevel));
}

}

#endif

