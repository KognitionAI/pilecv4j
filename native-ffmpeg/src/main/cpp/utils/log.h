#pragma once

#include <stdarg.h>

// =====================================================
// Utilities: logging
// =====================================================
#define MAX_LOG_LEVEL 5

#define PO(x) (x == nullptr ? "null" : x)

namespace pilecv4j {
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

#ifdef LOGGING
  bool isEnabled(LogLevel llevel);
  void log(LogLevel llevel, const char* component, const char *fmt, ...);
  void log(LogLevel llevel, const char* component, const char* fmt, va_list args);
  void setLogLevel(LogLevel ll);
#else
  inline static void log(...) {}
  inline static void setLogLevel(LogLevel ll) {}
  inline static bool isEnabled(...) { return false; }
#endif
}

