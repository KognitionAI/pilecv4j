/*
 * UriRemuxer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _URIREMUXER_H_
#define _URIREMUXER_H_

#include "api/MediaProcessor.h"

#define DEFAULT_MAX_REMUX_ERRORS 20

namespace pilecv4j
{

/**
 * This is a media processor that will remux all video and audio packets that make
 * it to the handlePacket call.
 */
class UriRemuxer: public MediaProcessor
{
  std::string fmt;
  bool fmtNull;
  std::string outputUri;
  uint32_t maxRemuxErrorCount;

  bool cleanupIoContext = false; // track whether or not we need to cleanup the io context
  AVFormatContext *output_format_context = nullptr;
  int* streams_list = nullptr;
  int64_t startTime = -1;
  uint32_t remuxErrorCount = 0;

  void cleanupRemux(AVFormatContext* output_format_context, bool cleanupIoContext);

  // sets output_format_context, cleanupIoContext, and streams_list
  uint64_t setupRemux(AVFormatContext* avformatCtx);
  uint64_t remuxPacket(AVFormatContext* formatCtx,  const AVPacket * inPacket);

public:
  inline UriRemuxer(const char* pfmt, const char* poutputUri, uint32_t pmaxRemuxErrorCount = DEFAULT_MAX_REMUX_ERRORS) : fmt(pfmt == nullptr ? "" : pfmt),
                    fmtNull(pfmt == nullptr), outputUri(poutputUri), maxRemuxErrorCount(pmaxRemuxErrorCount) {}

  virtual ~UriRemuxer();

  virtual uint64_t setup(AVFormatContext* avformatCtx, const std::vector<std::tuple<std::string,std::string> >& options, bool* selectedStreams);

  virtual uint64_t preFirstFrame(AVFormatContext* avformatCtx);

  virtual uint64_t handlePacket(AVFormatContext* avformatCtx, AVPacket* pPacket, AVMediaType streamMediaType);
};

} /* namespace pilecv4j */

#endif /* _URIREMUXER_H_ */
