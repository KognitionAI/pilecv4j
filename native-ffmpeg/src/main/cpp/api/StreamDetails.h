
#ifndef _STREAMDETAILS_H_
#define _STREAMDETAILS_H_

extern "C" {
#include <libavformat/avformat.h>
}

#include <cstring>
#include <stdint.h>

#define MAX_CODEC_NAME_LEN 1024

namespace pilecv4j
{
namespace ffmpeg
{

struct StreamDetails {
  int32_t stream_index = -1;
  int32_t mediaType = AVMEDIA_TYPE_UNKNOWN;

  int32_t fps_num = -1;
  int32_t fps_den = -1;

  int32_t tb_num = -1;
  int32_t tb_den = -1;

  int32_t codec_id = -1;
  char* codecName = nullptr;

  inline ~StreamDetails() {
    if (codecName)
      delete [] codecName;
  }

  inline void setCodecName(const char* name) {
    size_t len = strlen(name) + 1;
    if (len < MAX_CODEC_NAME_LEN) {
      codecName = new char[len];
      strncpy(codecName, name, len);
    }
  }

  static uint64_t fillStreamDetails(AVFormatContext* formatCtx, StreamDetails** ppdetails, int* nb);

};

}
} /* namespace pilecv4j */

#endif
