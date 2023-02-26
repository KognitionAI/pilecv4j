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
#ifdef PILECV4J_ENABLE_TRACE_API
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

    log(TRACE, "%s[%s] Entering %s", spaces[depth], component, function);
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
      log(TRACE, "%s[%s] Leaving %s", spaces[depth], component, function);

    // need to pop the stack
    tlParent = this->parent;
  }
#endif
#endif
}
}


