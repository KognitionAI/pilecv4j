/*
 * UriRemuxer.cpp
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#define __INSIDE_DEFAULT_MUXER_SOURCE_CPP
#include <muxers/DefaultMuxer.h>
#undef __INSIDE_DEFAULT_MUXER_SOURCE_CPP

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

#define COMPONENT "DMUX"
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
static int write_packet_to_custom_output(void *opaque, uint8_t *buf, int buf_size) {
  PILECV4J_TRACE;
  DefaultMuxer const* c = (DefaultMuxer*)opaque;
  void* bufForCallback = c->ioBufferToWriteToJava;
  const write_buffer callback = c->dataSupplyCallback;
  memcpy(bufForCallback, buf, (unsigned long) buf_size);
  uint64_t ret = static_cast<int32_t>((*callback)(buf_size));
  llog(TRACE, "Result of writing %d bytes to Java: %ld", buf_size, (long)ret);
  return buf_size;
}

static int64_t seek_in_custom_output(void *opaque, int64_t offset, int whence) {
  PILECV4J_TRACE;
  DefaultMuxer const* c = (DefaultMuxer*)opaque;
  const seek_buffer_out seek = c->seekCallback;

  int64_t ret = (*seek)(offset, whence);
  llog(DEBUG, "seeking to %ld from 0x%x, results: %ld", (long)offset, (int)whence, (long)ret);
  return ret;
}

DefaultMuxer::~DefaultMuxer() {
  PILECV4J_TRACE;
  if (!closed)
    close();
}

void DefaultMuxer::fail() {
  PILECV4J_TRACE;
  cleanup(false);
}

uint64_t DefaultMuxer::close() {
  PILECV4J_TRACE;
  if (!closed) {
    cleanup(true);
    closed = true;
    return 0;
  }
  return 0;
}

void DefaultMuxer::cleanup(bool writeTrailer) {
  PILECV4J_TRACE;

  // This will free the output_format_context
  if (output_format_context) {
    if (writeTrailer && readyCalled) {
      llog(TRACE, "Writing trailer");
      //https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga7f14007e7dc8f481f054b21614dfec13
      av_write_trailer(output_format_context);
    }

    if (cleanupIoContext) {
      llog(TRACE, "Closing custom avio");
      cleanupIoContext = false;
      avio_closep(&output_format_context->pb);
      output_format_context->pb = nullptr;
    }

    llog(TRACE, "Freeing output_format_context");
    avformat_free_context(output_format_context);
    output_format_context = nullptr;
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
    ioContext = nullptr;
  }
  //========================================================================
  if (ioBufferToWriteToJava) {
    free(ioBufferToWriteToJava);
    ioBufferToWriteToJava = nullptr;
  }
}

// sets output_format_context
uint64_t DefaultMuxer::allocateOutputContext(AVFormatContext** ofcpp) {
  PILECV4J_TRACE;
  int ret = 0;
  const char* loutputUri = outputUriNull ? nullptr : outputUri.c_str();
  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  llog(DEBUG, "allocateOutputContext Output: [%s, %s]", PO(lfmt), PO(loutputUri));

  *ofcpp = nullptr;
  ret = avformat_alloc_output_context2(ofcpp, nullptr, lfmt, loutputUri);
  if (!(*ofcpp)) {
    llog(ERROR, "Failed to allocate output format context using a format of \"%s\" and an output file of \"%s\"",
        lfmt == nullptr ? "[NULL]" : lfmt, loutputUri);
    fail();
    return ret < 0 ? MAKE_AV_STAT(ret) : MAKE_AV_STAT(AVERROR_UNKNOWN);
  }

  return 0;
}

uint64_t DefaultMuxer::open() {
  PILECV4J_TRACE;
  uint64_t iret = 0;

  // either the dataSupplyCallback is null and the outputUri is set, or
  // the dataSupplyCallback is not null and the outputUri may or may not be set.
  //
  // if the dataSupplyCallback is not set but the outputUri is, then we're NOT doing custom io
  // if the dataSupplyCallback is NOT set and the outputUri is also not set, then we're
  // in a bad state and can't open.
  if (!dataSupplyCallback && outputUriNull) {
    llog(ERROR, "Cannot open an output without setting either the write callback or the output uri or both");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  if (isError(iret = allocateOutputContext(&output_format_context))) {
    llog(ERROR, "Failed to allocate the output context for muxing");
    return iret;
  }

  return 0;
}

uint64_t DefaultMuxer::ready() {
  PILECV4J_TRACE;

  int ret = 0;
  const char* loutputUri = outputUriNull ? nullptr : outputUri.c_str();
  const char* lfmt = fmtNull ? nullptr : fmt.c_str();

  // https://ffmpeg.org/doxygen/trunk/group__lavf__misc.html#gae2645941f2dc779c307eb6314fd39f10
  av_dump_format(output_format_context, 0, loutputUri, 1);

  // if we're doing custom output ...
  if (dataSupplyCallback) {
    if (isEnabled(TRACE)) {
      llog(TRACE, "Custom output using the callback at %" PRId64, (uint64_t)dataSupplyCallback);
      if (seekable())
        llog(TRACE, "  ... and a seek callback at %" PRId64, (uint64_t)seekCallback);
    }
    // check if ready was called already
    if (ioBuffer != nullptr) {
      llog(ERROR, "It appears the ready has been called twice on the DefaultMuxer");
      fail();
      return MAKE_P_STAT(ALREADY_SET);
    }

    ioBuffer = (uint8_t*)av_malloc(PCV4J_CUSTOMIO_OUTPUT_BUFSIZE * sizeof(uint8_t));

    // according to the docs on avformat_open_input:
    //     @note If you want to use custom IO, preallocate the format context and set its pb field.
    // So we're assuming that if the formatCtx->pb is set then the url can be null
    ioContext = avio_alloc_context(ioBuffer,PCV4J_CUSTOMIO_OUTPUT_BUFSIZE,AVIO_FLAG_WRITE,this,
        nullptr,
        write_packet_to_custom_output,
        seekable() ? seek_in_custom_output : nullptr);

    // setup the AVFormatContext for the custom io. See above note.
    llog(TRACE, "AVFMT_NOFILE set: %s", (output_format_context->flags & AVFMT_NOFILE) ? "true" : "false");
    output_format_context->pb = ioContext;
    //output_format_context->flags |= AVFMT_FLAG_CUSTOM_IO;
  } else {
    // otherwise we just use the outputUri. We already checked to make sure that if the dataSupplyCallback
    // is null then the outputUri MUST be set.
    if (!(output_format_context->oformat->flags & AVFMT_NOFILE)) {
      ret = avio_open(&output_format_context->pb, loutputUri, AVIO_FLAG_WRITE);
      if (ret < 0) {
        llog(ERROR, "Could not open output file '%s'", loutputUri);
        fail();
        return MAKE_AV_STAT(ret);
      }
      cleanupIoContext = true; // we need to close what we opened.
    } else
      llog(INFO, "AVFMT_NOFILE flag is set");
  }

  // write the header
  // https://ffmpeg.org/doxygen/trunk/group__lavf__encoding.html#ga18b7b10bb5b94c4842de18166bc677cb
  ret = avformat_write_header(output_format_context, nullptr);
  if (ret < 0) {
    llog(ERROR, "Error occurred when writing the header to the output");
    fail();
    return MAKE_AV_STAT(ret);
  }

  readyCalled = true;
  return 0;
}

uint64_t DefaultMuxer::createNextStream(AVCodecParameters* codecPars, int* stream_index_out) {
  if (!output_format_context) {
    llog(ERROR, "Cannot create a stream without a valid output_format_context already having been created. Did 'open' succeed?");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  AVStream* stream = nullptr;
  uint64_t ret = createStreamFromCodecParams(output_format_context, codecPars, &stream);
  if (!isError(ret)){
    createdStreams = true;
    if (stream_index_out)
      *stream_index_out = stream->index;
  }
  return ret;
}

uint64_t DefaultMuxer::createNextStream(AVCodecContext* codecc, int* stream_index_out) {
  if (!output_format_context) {
    llog(ERROR, "Cannot create a stream without a valid output_format_context already having been created. Did 'open' succeed?");
    return MAKE_P_STAT(NO_OUTPUT);
  }

  AVStream* stream = nullptr;
  uint64_t ret = createStreamFromCodec(output_format_context, codecc, &stream);
  if (!isError(ret)) {
    createdStreams = true;
    if (stream_index_out)
      *stream_index_out = stream->index;

    // copy the timebase from the codec context as a suggestion to the muxer when ready/avformat_write_header
    // which may very well modify it
    stream->time_base = codecc->time_base;

    // set the codecpar on the stream from the codec context
    ret = MAKE_AV_STAT(avcodec_parameters_from_context(stream->codecpar, codecc));
    if (isError(ret))
      llog(ERROR, "could not fill codec parameters");
  }
  return ret;
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {
  KAI_EXPORT uint64_t pcv4j_ffmpeg2_defaultMuxer_create(const char* pfmt, const char* poutputUri, write_buffer callback, seek_buffer_out seek) {
    PILECV4J_TRACE;
    Muxer* ret = new DefaultMuxer(pfmt, poutputUri, callback, seek);
    return (uint64_t)ret;
  }

  KAI_EXPORT void* pcv4j_ffmpeg2_defaultMuxer_buffer(uint64_t ctx) {
    PILECV4J_TRACE;
    DefaultMuxer* c = (DefaultMuxer*)ctx;
    return fetchBuffer(c);
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg2_defaultMuxer_bufferSize(uint64_t ctx) {
    PILECV4J_TRACE;
    return PCV4J_CUSTOMIO_OUTPUT_BUFSIZE;
  }
}

}
} /* namespace pilecv4j */

