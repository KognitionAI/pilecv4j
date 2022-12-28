#include "log.h"
#include "common/kog_exports.h"

#include <stdio.h>
#include <string>
#include <thread>

#define LOGGING_IOSTREAM stderr

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

// =====================================================
#endif

namespace pilecv4j {
namespace image {

#ifdef LOGGING

static LogLevel logLevel = INFO;

bool isEnabled(LogLevel llevel) {
  return logLevel <= llevel;
}

void setLogLevel(LogLevel ll) {
  logLevel = ll;
}

#define LOG_PREAMBLE \
    char hex_string[1024]; \
    size_t tid = std::hash<std::thread::id>{}(std::this_thread::get_id()); \
    sprintf(hex_string, "%lx", (unsigned long)tid); \
    fputs(component, LOGGING_IOSTREAM); \
    fputs( ": [", LOGGING_IOSTREAM ); \
    fputs(hex_string, LOGGING_IOSTREAM); \
    fputs( "] [", LOGGING_IOSTREAM); \
    fputs( logLevelNames[llevel], LOGGING_IOSTREAM ); \
    fputs( "] ", LOGGING_IOSTREAM)


void log(LogLevel llevel, const char* component, const char* fmt, va_list args) {
  if (logLevel <= llevel) {
    LOG_PREAMBLE;
    vfprintf( LOGGING_IOSTREAM, fmt, args );
    fputs( "\n", LOGGING_IOSTREAM );
  }
}

void log(LogLevel llevel, const char* component, const char *fmt, ...)
{
  if (logLevel <= llevel) {
    va_list args;
    LOG_PREAMBLE;
    va_start( args, fmt );
    vfprintf( LOGGING_IOSTREAM, fmt, args );
    va_end( args );
    fputs( "\n", LOGGING_IOSTREAM );
  }
}

static void logNoCr(LogLevel llevel, const char* component, const char *fmt, ...)
{
  if (logLevel <= llevel) {
    va_list args;
    LOG_PREAMBLE;
    va_start( args, fmt );
    vfprintf( LOGGING_IOSTREAM, fmt, args );
    va_end( args );
  }
}

#ifdef ENABLE_TRACE_API
  static thread_local TraceGuard* tlParent;

  static char** getSpacesArray(int size)
  {
    char** ret = new char*[size];
    for (int i = 0; i < size; i++)
    {
      ret[i] = new char[i + 1];

      int j;
      for (j = 0; j < i; j++)
        ret[i][j] = ' ';
      ret[i][j] = 0;
    }
    return ret;
  }

  static char** spaces = getSpacesArray(256);

  const char* TraceGuard::getSpaces() { return spaces[depth]; }

  TraceGuard::TraceGuard(const char* _component, const char* _function) :component(_component), function(_function)
  {
    parent = tlParent;
    depth = parent == NULL ? 0 : parent->depth + 1;

    tlParent = this;

    log(TRACE, component, "%s Entering %s", spaces[depth], function);
  }

  TraceGuard::TraceGuard() : component(nullptr), function(nullptr)
  {
    parent = tlParent;
    depth = parent == NULL ? 0 : parent->depth + 1;
    tlParent = this;
    // silent
  }

  TraceGuard::~TraceGuard()
  {
    if (function)
      log(TRACE, component, "%s Leaving %s", spaces[depth], function);

    // need to pop the stack
    tlParent = this->parent;
  }
#endif // ENABLE_TRACE_API
#else

  void qaicLogCallback(QLogLevel logLevel, const char *logMessage) {  }

  std::string lempty_string = "";
  const std::string& empty_string() {
    return lempty_string;
  }

#endif // LOGGING

extern "C" {

  KAI_EXPORT void pilecv4j_image_setLogLevel(int32_t plogLevel) {
#ifdef LOGGING
    setLogLevel(static_cast<LogLevel>(plogLevel));
#endif
  }

}
}
}



