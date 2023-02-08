/*
 * CustomMediaDataSource.cpp
 *
 *  Created on: Jul 6, 2022
 *      Author: jim
 */

#include "utils/log.h"
#include "common/kog_exports.h"

#define __INSIDE_CUSTOM_MEDIA_DATA_SOURCE_CPP
#include "sources/CustomMediaDataSource.h"

namespace pilecv4j
{
namespace ffmpeg
{

#define COMPONENT "CMDS"

inline static void log(LogLevel llevel, const char *fmt, ...) {
  va_list args;
  va_start( args, fmt );
  log( llevel, COMPONENT, fmt, args );
  va_end( args );
}

//========================================================================
/**
 * AV compliant callbacks for custom IO.
 */
static int read_packet_from_custom_source(void *opaque, uint8_t *buf, int buf_size) {
  CustomMediaDataSource const* c = (CustomMediaDataSource*)opaque;
  const void* bufForCallback = c->ioBufferToFillFromJava;
  const fill_buffer callback = c->dataSupplyCallback;
  int32_t numBytesRead = static_cast<int32_t>((*callback)(buf_size));
  if (numBytesRead < 0) {
    log(DEBUG, "call to read bytes returned an error code: %s", av_err2str(numBytesRead));
    return numBytesRead;
  }

  log(TRACE, "num bytes read: %d", numBytesRead);
  if (numBytesRead != 0) {
    if (numBytesRead > buf_size) {
      log(ERROR, "Too many bytes (%d) written when the buffer size is only %d", numBytesRead, buf_size);
      numBytesRead = 0;
    } else {
      memcpy(buf, bufForCallback, numBytesRead);
    }
  }
  return numBytesRead == 0 ? AVERROR(EAGAIN) : numBytesRead;
}

static int64_t seek_in_custom_source(void *opaque, int64_t offset, int whence) {
  CustomMediaDataSource const* c = (CustomMediaDataSource*)opaque;
  const seek_buffer seek = c->seekCallback;

  int64_t ret = (*seek)(offset, whence);
  log(DEBUG, "seeking to %ld from 0x%x, results: %ld", (long)offset, (int)whence, (long)ret);
  return ret;
}
//========================================================================

uint64_t CustomMediaDataSource::open(AVFormatContext** preallocatedAvFormatCtx, AVDictionary** opts)
{
  // check if open was called already
  if (ioBuffer != nullptr) {
    avformat_free_context(*preallocatedAvFormatCtx);
    *preallocatedAvFormatCtx = nullptr;
    return MAKE_P_STAT(ALREADY_SET);
  }

  ioBuffer = (uint8_t*)av_malloc(PCV4J_CUSTOMIO_BUFSIZE * sizeof(uint8_t));

  // according to the docs on avformat_open_input:
  //     @note If you want to use custom IO, preallocate the format context and set its pb field.
  // So we're assuming the if the formatCtx->pb is set then the url can be null
  ioContext = avio_alloc_context(ioBuffer,PCV4J_CUSTOMIO_BUFSIZE,0,this,
          read_packet_from_custom_source,
          nullptr,
          seekable() ? seek_in_custom_source : nullptr);

  // setup the AVFormatContext for the custom io. See above note.
  (*preallocatedAvFormatCtx)->pb = ioContext;

  // according to the docs for avformat_open_input, "a user-supplied AVFormatContext will be freed on failure."
  return MAKE_AV_STAT(avformat_open_input(preallocatedAvFormatCtx, nullptr, nullptr, opts));
}

CustomMediaDataSource::~CustomMediaDataSource()
{
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
  if (ioBufferToFillFromJava)
    free(ioBufferToFillFromJava);
}

//========================================================================
// Everything here in this extern "C" section is callable from Java
//========================================================================
extern "C" {

KAI_EXPORT uint64_t pcv4j_ffmpeg2_customMediaDataSource_create() {
  CustomMediaDataSource* ret = new CustomMediaDataSource();

  return (uint64_t)((MediaDataSource*)ret);
}

KAI_EXPORT uint64_t pcv4j_ffmpeg2_customMediaDataSource_set(uint64_t ctx, fill_buffer callback, seek_buffer seekCallback) {
  CustomMediaDataSource* c = (CustomMediaDataSource*)ctx;
  c->set(callback, seekCallback);
  return 0;
}

KAI_EXPORT void* pcv4j_ffmpeg2_customMediaDataSource_buffer(uint64_t ctx) {
  CustomMediaDataSource* c = (CustomMediaDataSource*)ctx;
  return fetchBuffer(c);
}

KAI_EXPORT int32_t pcv4j_ffmpeg2_customMediaDataSource_bufferSize(uint64_t ctx) {
  return PCV4J_CUSTOMIO_BUFSIZE;
}

}

}
} /* namespace pilecv4j */

