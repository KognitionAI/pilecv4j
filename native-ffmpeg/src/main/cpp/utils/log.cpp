#include <thread>
#include <stdio.h>
#include <string>

#include "utils/log.h"

#include "common/kog_exports.h"

extern "C" {
#include <libavutil/log.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
#include <libavutil/rational.h>
#include <libavformat/avformat.h>
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
#endif

namespace pilecv4j {
namespace ffmpeg
{
#ifdef LOGGING

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

static AVRational nullRat{ -1, -1 };

void logPacket(LogLevel ll, const char* component, const char* header, AVPacket* packet, AVFormatContext* ctx) {
  if (isEnabled(TRACE)) {
    const int stream_index = packet->stream_index;
    const AVStream* stream = (stream_index >= 0 && stream_index < ctx->nb_streams) ? ctx->streams[stream_index] : nullptr;
    const AVMediaType mt = stream ? (stream->codecpar ? stream->codecpar->codec_type : AVMEDIA_TYPE_UNKNOWN) : AVMEDIA_TYPE_UNKNOWN;
    const AVRational* tb = stream ? &(stream->time_base) : &nullRat;

    log(TRACE, component, "%s: %s, stream: %d, num bytes: %d, key frame: %s, pts: %" PRId64 ", dts: %" PRId64 ", timebase: [ %d / %d ]",
        header == nullptr ? "" : header, av_get_media_type_string(mt), stream_index, packet->size,
        (packet->flags & AV_PKT_FLAG_KEY) ? "true" : "false", packet->pts, packet->dts,
            tb->num, tb->den);
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
#endif
#endif

extern "C" {

KAI_EXPORT void pcv4j_ffmpeg2_logging_setLogLevel(int32_t plogLevel) {
  setLogLevel(static_cast<LogLevel>(plogLevel));
}

}
}
}


