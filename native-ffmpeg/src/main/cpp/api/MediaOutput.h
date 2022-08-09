/*
 * MediaOutput.h
 *
 *  Created on: Aug 9, 2022
 *      Author: jim
 */

extern "C" {
#include <libavformat/avformat.h>
}

#ifndef _PILECV4J_FFMPEG_MEDIAOUTPUT_H_
#define _PILECV4J_FFMPEG_MEDIAOUTPUT_H_

namespace pilecv4j
{
namespace ffmpeg
{

class MediaOutput
{
  bool closed = false;
  AVFormatContext* output_format_context = nullptr;
protected:

  inline void setFormatContext(AVFormatContext* ofc) {
    output_format_context = ofc;
  }

public:
  inline MediaOutput() = default;

  virtual ~MediaOutput();

  inline AVFormatContext* getFormatContext() {
    return output_format_context;
  }

  inline bool isClosed() {
    return closed;
  }

  /**
   * By default, if the output_format_context is not null, this will write the trailer
   * using <em>av_write_trailer</em>
   */
  virtual uint64_t close();

  /**
   * Implementers need to create the output_format_context here. This could mean calling
   * <em>avio_open</em> or setting up a custom output buffer using
   */
  virtual uint64_t allocateOutputContext(AVFormatContext ** output_format_context) = 0;

  /**
   * Implementers need to open the output here.
   */
  virtual uint64_t openOutput(AVDictionary** opts) = 0;

  /**
   * On failure there can be some resources that need closing. This will be called to close them.
   */
  virtual void fail();
};

}
} /* namespace pilecv4j */

#endif /* _PILECV4J_FFMPEG_MEDIAOUTPUT_H_ */
