/*
 * UriMuxer.h
 *
 *  Created on: Jan 31, 2023
 *      Author: jim
 */

#ifndef SRC_MAIN_CPP_MUXERS_URIMUXER_H_
#define SRC_MAIN_CPP_MUXERS_URIMUXER_H_

#include "api/Muxer.h"

namespace pilecv4j
{
namespace ffmpeg
{

class UriMuxer : public Muxer
{
  std::string fmt;
  bool fmtNull;
  std::string outputUri;
  bool cleanupIoContext = false; // track whether or not we need to cleanup the io context

  void cleanup(bool writeTrailer);

public:
  inline UriMuxer(const char* pfmt, const char* poutputUri) :
    fmt(pfmt == nullptr ? "" : pfmt), fmtNull(pfmt == nullptr), outputUri(poutputUri) {}

  virtual ~UriMuxer();

//  virtual uint64_t setup(PacketSourceInfo* mediaSource, const std::vector<std::tuple<std::string,std::string> >& options);
};

}
} /* namespace pilecv4j */

#endif /* SRC_MAIN_CPP_MUXERS_URIMUXER_H_ */
