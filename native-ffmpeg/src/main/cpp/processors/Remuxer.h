/*
 * UriRemuxer.h
 *
 *  Created on: Jul 7, 2022
 *      Author: jim
 */

#ifndef _pilecv4j_ffmpeg_REMUXER_H_
#define _pilecv4j_ffmpeg_REMUXER_H_

#include "api/MediaProcessor.h"

#define DEFAULT_MAX_REMUX_ERRORS 20

namespace pilecv4j
{
namespace ffmpeg
{

class Muxer;

/**
 * This is a media processor that will remux all video and audio packets that make
 * it to the handlePacket call.
 */
class Remuxer: public MediaProcessor
{
  uint32_t maxRemuxErrorCount;

  int* streams_list = nullptr;
  int64_t startTime = -1;
  uint32_t remuxErrorCount = 0;

  // sets output_format_context, cleanupIoContext, and streams_list
  uint64_t remuxPacket(const AVPacket * inPacket);

  Muxer* output;
  int number_of_streams = -1;
  std::vector<std::tuple<std::string,std::string> > options;

  bool alreadySetup = false;

  uint64_t setupStreams(AVCodecParameters** in_codecparpp);
  AVRational* streamTimeBases = nullptr;

protected:

public:
  inline Remuxer(Muxer* poutput, uint32_t pmaxRemuxErrorCount = DEFAULT_MAX_REMUX_ERRORS) :
                    maxRemuxErrorCount(pmaxRemuxErrorCount), output(poutput) {}

  virtual ~Remuxer();

  virtual uint64_t close();

  virtual uint64_t setup(PacketSourceInfo* psi, std::vector<std::tuple<std::string,std::string> >& options);

  virtual uint64_t preFirstFrame();

  virtual uint64_t handlePacket(AVPacket* pPacket, AVMediaType streamMediaType);

};

}
} /* namespace pilecv4j */

#endif /* _pilecv4j_ffmpeg_REMUXER_H_ */
