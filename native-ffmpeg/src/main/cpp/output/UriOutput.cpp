/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#include "output/UriOutput.h"

#include "utils/pilecv4j_ffmpeg_utils.h"
#include "utils/log.h"

#include "common/kog_exports.h"

extern "C" {
#include <libswscale/swscale.h>
}

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "RMUX"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

UriOutput::~UriOutput() {
  PILECV4J_TRACE;
  close();
}

// sets output_format_context
uint64_t UriOutput::allocateOutputContext(AVFormatContext** ofcpp) {
  PILECV4J_TRACE;
  int ret = 0;
  const char* loutputUri = outputUri.c_str();
  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  llog(DEBUG, "UriRemuxer: [%s, %s]", PO(lfmt), PO(loutputUri));

  *ofcpp = nullptr;
  ret = avformat_alloc_output_context2(ofcpp, nullptr, lfmt, loutputUri);
  if (!(*ofcpp)) {
    llog(ERROR, "Failed to allocate output format context using a format of \"%s\" and an output file of \"%s\"",
        lfmt == nullptr ? "[NULL]" : lfmt, loutputUri);
    return ret < 0 ? MAKE_AV_STAT(ret) : MAKE_AV_STAT(AVERROR_UNKNOWN);
  }
  setFormatContext(*ofcpp);

  return 0;
}

uint64_t UriOutput::openOutput(AVDictionary** opts) {
  PILECV4J_TRACE;

  AVFormatContext* output_format_context = getFormatContext();
  if (!output_format_context) {
    llog(ERROR, "No output format create yet. Cannot open the output.");
    return MAKE_P_STAT(NO_FORMAT);
  }

  int ret = 0;
  const char* loutputUri = outputUri.c_str();
  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  // https://ffmpeg.org/doxygen/trunk/group__lavf__misc.html#gae2645941f2dc779c307eb6314fd39f10
  av_dump_format(output_format_context, 0, loutputUri, 1);

  if (!(output_format_context->oformat->flags & AVFMT_NOFILE)) {
    ret = avio_open(&output_format_context->pb, loutputUri, AVIO_FLAG_WRITE);
    if (ret < 0) {
      llog(ERROR, "Could not open output file '%s'", loutputUri);
      return MAKE_AV_STAT(ret);
    }
    cleanupIoContext = true; // we need to close what we opened.
  } else
    llog(INFO, "AVFMT_NOFILE flag is set");

  return 0;
}

// close the file if there is one.
void UriOutput::cleanup(bool writeTrailer) {
  PILECV4J_TRACE;
  AVFormatContext* output_format_context = getFormatContext();

  // This will free the output_format_context
  if (output_format_context) {
    if (writeTrailer) {
      llog(TRACE, "Writing trailer");
      //https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga7f14007e7dc8f481f054b21614dfec13
      av_write_trailer(output_format_context);
    }

    if (cleanupIoContext) {
      avio_closep(&output_format_context->pb);
      output_format_context->pb = nullptr;
    }
    cleanupIoContext = false;

    avformat_free_context(output_format_context);
    setFormatContext(nullptr);
  }
}

uint64_t UriOutput::close() {
  PILECV4J_TRACE;
  // we cannot delegate to MediaOutput::close because
  // it will av_write_trailer and then free the format context.
  // but in between those operations we need to avio_closep
  if (!isClosed()) {
    cleanup(true);
    return MediaOutput::close();
  }
  return 0;
}

void UriOutput::fail() {
  PILECV4J_TRACE;
  cleanup(false);
  MediaOutput::fail();
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_uriOutput_create(const char* pfmt, const char* poutputUri) {
  PILECV4J_TRACE;
  MediaOutput* ret = new UriOutput(pfmt, poutputUri);
  return (uint64_t)ret;
}

}

}
} /* namespace pilecv4j */

