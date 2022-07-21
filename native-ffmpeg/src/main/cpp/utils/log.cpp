#include <thread>
#include <stdio.h>
#include <string>

#include "utils/log.h"

#include "kog_exports.h"

extern "C" {
#include <libavutil/log.h>
}

#define LOGGING_IOSTREAM stderr

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
namespace ffmpeg
{
static LogLevel logLevel = INFO;

bool isEnabled(LogLevel llevel) {
  return logLevel <= llevel;
}

void setLogLevel(LogLevel ll) {
  logLevel = ll;

  av_log_set_level(avLogLevelLookup[ll]);
}

void log(LogLevel llevel, const char* component, const char* fmt, va_list args) {
  if (logLevel <= llevel) {
    char hex_string[1024];
    size_t tid = std::hash<std::thread::id>{}(std::this_thread::get_id());
    //std::string tidstr = std::to_string(tid);
    sprintf(hex_string, "%lx", (unsigned long)tid);
    fputs(component, LOGGING_IOSTREAM);
    fputs( ": [", LOGGING_IOSTREAM );
    //fputs( tidstr.c_str(), LOGGING_IOSTREAM);
    fputs(hex_string, LOGGING_IOSTREAM);
    fputs( "] [", LOGGING_IOSTREAM);
    fputs( logLevelNames[llevel], LOGGING_IOSTREAM );
    fputs( "] ", LOGGING_IOSTREAM);
    vfprintf( LOGGING_IOSTREAM, fmt, args );
    fputs( "\n", LOGGING_IOSTREAM );
  }
}

void log(LogLevel llevel, const char* component, const char *fmt, ...)
{
  if (logLevel <= llevel) {
    char hex_string[1024];
    size_t tid = std::hash<std::thread::id>{}(std::this_thread::get_id());
    //std::string tidstr = std::to_string(tid);
    sprintf(hex_string, "%lx", (unsigned long)tid);
    va_list args;
    fputs(component, LOGGING_IOSTREAM);
    fputs( ": [", LOGGING_IOSTREAM );
    //fputs( tidstr.c_str(), LOGGING_IOSTREAM);
    fputs(hex_string, LOGGING_IOSTREAM);
    fputs( "] [", LOGGING_IOSTREAM);
    fputs( logLevelNames[llevel], LOGGING_IOSTREAM );
    fputs( "] ", LOGGING_IOSTREAM);
    va_start( args, fmt );
    vfprintf( LOGGING_IOSTREAM, fmt, args );
    va_end( args );
    fputs( "\n", LOGGING_IOSTREAM );
  }
}

extern "C" {

KAI_EXPORT void pcv4j_ffmpeg2_logging_setLogLevel(int32_t plogLevel) {
  setLogLevel(static_cast<LogLevel>(plogLevel));
}

}
}
}

#endif

