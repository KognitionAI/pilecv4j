#include "utils/log.h"
#include "common/kog_exports.h"

#include <stdio.h>
#include <string>
#include <thread>
#include <atomic>

#define LOGGING_IOSTREAM stderr

// currently (11/15/2022) LOGGING is defined by default in the log.h file.
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
namespace ipc {

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
    sprintf(hex_string, "%llx", (unsigned long long)tid); \
    fputs(component, LOGGING_IOSTREAM); \
    fputs( ": [", LOGGING_IOSTREAM ); \
    fputs(hex_string, LOGGING_IOSTREAM); \
    fputs( "] [", LOGGING_IOSTREAM); \
    fputs( logLevelNames[llevel], LOGGING_IOSTREAM ); \
    fputs( "] ", LOGGING_IOSTREAM)

static std::atomic_bool log_lock{false};

void log(LogLevel llevel, const char* component, const char *fmt, ...)
{
  if (logLevel <= llevel) {
    {
      bool expected = false;
      while(!log_lock.compare_exchange_weak(expected, true, std::memory_order_acquire)) {
        expected = false;
      }
    }
    va_list args;
    LOG_PREAMBLE;
    va_start( args, fmt );
    vfprintf( LOGGING_IOSTREAM, fmt, args );
    va_end( args );
    fputs( "\n", LOGGING_IOSTREAM );
    fflush(LOGGING_IOSTREAM);
    {
      log_lock.store(false, std::memory_order_release);
    }
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
#endif // AI100_ENABLE_TRACE_API
#else

  std::string lempty_string = "";
  const std::string& empty_string() {
    return lempty_string;
  }

#endif // LOGGING

extern "C" {

  KAI_EXPORT void pcv4j_ipc_logging_setLogLevel(int32_t plogLevel) {
#ifdef LOGGING
    setLogLevel(static_cast<LogLevel>(plogLevel));
#endif
  }

}
}
}



