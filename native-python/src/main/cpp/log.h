#pragma once

#include <stdarg.h>

// =====================================================
// Utilities: logging
// =====================================================
#define MAX_LOG_LEVEL 5

namespace pilecv4j {
namespace python {
  /**
   * Log levels and log level names.
   */
  enum LogLevel {
    TRACE=0,
    DEBUG=1,
    INFO=2,
    WARN=3,
    ERROR=4,
    FATAL=5
  };

  inline bool isEnabled(LogLevel llevel) {
    extern LogLevel logLevel;
    return logLevel <= llevel;
  }

#ifdef LOGGING
#ifdef PILECV4J_ENABLE_TRACE_API
  class TraceGuard
  {
    const char* function;
    const char* component;
  public:
    TraceGuard* parent;
    int depth;

    const char* getSpaces();

    explicit TraceGuard(const char* _component, const char* _function);
    TraceGuard();
    ~TraceGuard();
  };

#ifdef _MSC_VER
#define __PRETTY_FUNCTION__ __FUNCTION__
#endif

#define RAW_PILECV4J_TRACE(c) TraceGuard _tg(c, __PRETTY_FUNCTION__)
#else
#define RAW_PILECV4J_TRACE(c)
#endif
  void log(LogLevel llevel, const char *fmt, ...);
  void setLogLevel(LogLevel ll);
#else
  inline static void log(...) {}
  inline static void setLogLevel(LogLevel ll) {}
#define RAW_PILECV4J_TRACE(c)
#endif
}
}

