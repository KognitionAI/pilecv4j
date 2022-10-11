/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#define __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP
#include "output/CustomOutput.h"
#undef __INSIDE_CUSTOM_OUTPUT_SOURCE_CPP

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

#define COMPONENT "CORM"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

inline static void llog(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

/**
 * AV compliant callback for custom IO.
 */
static int write_packet_to_custom_source(void *opaque, uint8_t *buf, int buf_size) {
  PILECV4J_TRACE;
  CustomOutput const* c = (CustomOutput*)opaque;
  void* bufForCallback = c->ioBufferToWriteToJava;
  const write_buffer callback = c->dataSupplyCallback;
  memcpy(bufForCallback, buf, (unsigned long) buf_size);
  uint64_t ret = static_cast<int32_t>((*callback)(buf_size));
  llog(TRACE, "Result of writing %d bytes to Java: %ld", buf_size, (long)ret);
  return buf_size;
}

CustomOutput::~CustomOutput() {
  PILECV4J_TRACE;
}

void CustomOutput::fail() {
  cleanup(false);
  MediaOutput::fail();
}

uint64_t CustomOutput::close() {
  if (!isClosed()) {
    cleanup(true);
    return MediaOutput::close();
  }
  return 0;
}

void CustomOutput::cleanup(bool writeTrailer) {

  AVFormatContext* output_format_context = getFormatContext();

  // This will free the output_format_context
  if (output_format_context) {
    if (writeTrailer) {
      llog(TRACE, "Writing trailer");
      //https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga7f14007e7dc8f481f054b21614dfec13
      av_write_trailer(output_format_context);
    }

    avformat_free_context(output_format_context);
    setFormatContext(nullptr);
  }

  //========================================================================
  // This is to compensate for a bug (or stupidity) in FFMpeg. See :
  // https://stackoverflow.com/questions/9604633/reading-a-file-located-in-memory-with-libavformat
  // ... and search for "double free". Then ALSO follow the link to
  // https://lists.ffmpeg.org/pipermail/libav-user/2012-December/003257.html
  // Then look at aviobuf.c in the source code and search for the function
  // definition for ffio_set_buf_size. You can see that if Ffmpeg decides
  // to shrink the buffer, it will reallocate a buffer and free the one that's
  // there already.
  if (ioContext) {
    if (ioBuffer && ioContext->buffer == ioBuffer)
      av_free(ioBuffer);
    else
      av_free(ioContext->buffer);
    av_free(ioContext);
  }
  //========================================================================
  if (ioBufferToWriteToJava)
    free(ioBufferToWriteToJava);
}

// sets output_format_context
uint64_t CustomOutput::allocateOutputContext(AVFormatContext** ofcpp) {
  PILECV4J_TRACE;
  int ret = 0;
  const char* loutputUri = outputUriNull ? nullptr : outputUri.c_str();
  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  llog(DEBUG, "allocateOutputContext CustomOutputRemuxer: [%s, %s]", PO(lfmt), PO(loutputUri));

  *ofcpp = nullptr;
  ret = avformat_alloc_output_context2(ofcpp, nullptr, lfmt, loutputUri);
  if (!(*ofcpp)) {
    llog(ERROR, "Failed to allocate output format context using a format of \"%s\" and an output file of \"%s\"",
        lfmt == nullptr ? "[NULL]" : lfmt, loutputUri);
    fail();
    return ret < 0 ? MAKE_AV_STAT(ret) : MAKE_AV_STAT(AVERROR_UNKNOWN);
  }
  setFormatContext(*ofcpp);

  return 0;
}

uint64_t CustomOutput::openOutput(AVDictionary** opts) {
  AVFormatContext* output_format_context = getFormatContext();
  if (!output_format_context) {
    llog(ERROR, "No output format create yet. Cannot open the output.");
    return MAKE_P_STAT(NO_FORMAT);
  }

  PILECV4J_TRACE;
  int ret = 0;
  const char* loutputUri = outputUri.c_str();
  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  // https://ffmpeg.org/doxygen/trunk/group__lavf__misc.html#gae2645941f2dc779c307eb6314fd39f10
  av_dump_format(output_format_context, 0, loutputUri, 1);

  // check if open was called already
  if (ioBuffer != nullptr) {
    llog(ERROR, "It appears the open has been called twice on the CustomOutputRemuxer");
    fail();
    return MAKE_P_STAT(ALREADY_SET);
  }

  ioBuffer = (uint8_t*)av_malloc(PCV4J_CUSTOMIO_OUTPUT_BUFSIZE * sizeof(uint8_t));

  // according to the docs on avformat_open_input:
  //     @note If you want to use custom IO, preallocate the format context and set its pb field.
  // So we're assuming that if the formatCtx->pb is set then the url can be null
  ioContext = avio_alloc_context(ioBuffer,PCV4J_CUSTOMIO_OUTPUT_BUFSIZE,AVIO_FLAG_WRITE,this,
          nullptr,
          write_packet_to_custom_source,
          nullptr);

  // setup the AVFormatContext for the custom io. See above note.
  llog(TRACE, "AVFMT_NOFILE set: %s", (output_format_context->flags & AVFMT_NOFILE) ? "true" : "false");
  output_format_context->pb = ioContext;
  //output_format_context->flags |= AVFMT_FLAG_CUSTOM_IO;

  // write the header
  // https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga18b7b10bb5b94c4842de18166bc677cb
  ret = avformat_write_header(output_format_context, nullptr);
  if (ret < 0) {
    llog(ERROR, "Error occurred when opening output file\n");
    fail();
    return MAKE_AV_STAT(ret);
  }

  return 0;
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_customOutput_create(const char* pfmt, const char* poutputUri) {
    PILECV4J_TRACE;
    MediaOutput* ret = new CustomOutput(pfmt, poutputUri);
    return (uint64_t)ret;
  }

  KAI_EXPORT uint64_t pcv4j_ffmpeg2_customOutput_set(uint64_t ctx, write_buffer callback) {
    PILECV4J_TRACE;
    CustomOutput* c = (CustomOutput*)ctx;
    c->set(callback);
    return 0;
  }

  KAI_EXPORT void* pcv4j_ffmpeg2_customOutput_buffer(uint64_t ctx) {
    PILECV4J_TRACE;
    CustomOutput* c = (CustomOutput*)ctx;
    return fetchBuffer(c);
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg2_customOutput_bufferSize(uint64_t ctx) {
    PILECV4J_TRACE;
    return PCV4J_CUSTOMIO_OUTPUT_BUFSIZE;
  }
}

}
} /* namespace pilecv4j */

