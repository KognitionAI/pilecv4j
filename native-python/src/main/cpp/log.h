#pragma once

#include <stdarg.h>

#define LOGGING

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
  void log(LogLevel llevel, const char *fmt, ...);
  void setLogLevel(LogLevel ll);
#else
  inline static void log(...) {}
  inline static void setLogLevel(LogLevel ll) {}
#endif
}
}

