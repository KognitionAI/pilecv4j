#pragma once

#include <sstream>
#include <stdarg.h>

// =====================================================
// Utilities: logging
// =====================================================
#define MAX_LOG_LEVEL 5

#define PO(x) (x == nullptr ? "null" : x)

namespace pilecv4j {
namespace image {
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

#ifdef ENABLE_TRACE_API
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

#define RAW_PCV4J_IMAGE_TRACE(c) TraceGuard _tg(c, __PRETTY_FUNCTION__)
#else
#define RAW_PCV4J_IMAGE_TRACE(c)
#endif

// else !LOGGING
#else
#define RAW_PCV4J_IMAGE_TRACE(c)

  inline static void log(...) {}
  inline static void setLogLevel(LogLevel ll) {}
  inline static bool isEnabled(...) { return false; }
  inline static void logPacket(...) {}

  const std::string& empty_string();
  template<typename TENSOR> const std::string& stringify(const TENSOR& tensor) {
    return empty_string();
  }

#endif

}
}

