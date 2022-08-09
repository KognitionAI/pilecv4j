/*
 * UriRemuxer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _pilecv4j_ffmpeg_URIREMUXER_H_
#define _pilecv4j_ffmpeg_URIREMUXER_H_

#include "api/MediaOutput.h"

#include <string>

#define DEFAULT_MAX_REMUX_ERRORS 20

namespace pilecv4j
{
namespace ffmpeg
{

/**
 * This is a media processor that will remux all video and audio packets that make
 * it to the handlePacket call.
 */
class UriOutput: public MediaOutput
{
  std::string fmt;
  bool fmtNull;
  std::string outputUri;
  bool cleanupIoContext = false; // track whether or not we need to cleanup the io context

  void cleanup(bool writeTrailer);

public:
  inline UriOutput(const char* pfmt, const char* poutputUri) :
    fmt(pfmt == nullptr ? "" : pfmt), fmtNull(pfmt == nullptr), outputUri(poutputUri) {}

  virtual ~UriOutput();

  virtual uint64_t close();

  virtual uint64_t allocateOutputContext(AVFormatContext **);

  virtual uint64_t openOutput(AVDictionary** opts);

  virtual void fail();
};

}
} /* namespace pilecv4j */

#endif /* _pilecv4j_ffmpeg_URIREMUXER_H_ */
