#include "common/kog_exports.h"
#include "utils/pilecv4j_ffmpeg_utils.h"

#include <libavutil/hwcontext.h>

AVRational pilecv4j::ffmpeg::millisecondTimeBase = AVRational{1,1000};

// status message descriptions corresponding to the custom codes.
static const char* pcv4jStatMessages[MAX_PCV4J_CODE + 1] = {
    "OK",
    "Can't open another stream with the same context",
    "Context not in correct state for given operation",
    "Couldn't find a media stream in the given source OR stream index not set yet",
    "No supported media codecs available for the given source OR codec not set",
    "Failed to create a codec context",
    "Failed to create a frame",
    "Failed to create a packet",
    "Unsupported Codec",
    "No source set",
    "Resource (source, encoder input, etc) is already set",
    "Source cannot be set to null",
    "No processor set or attempt to set a null processor",
    "No image maker set.",
    "Failed to create codec.",
    "Option already set.",
    "The underlying stream seems to have changed in some important dimension.",
    "The stream selection failed.",
    "No format context",
    "There's no output set",
    "No packet source info",
    "Required parameter missing",
    "Failed to create a muxer",
};

/**
 * Error and status support.
 */
static const char* totallyUnknownError = "UNKNOWN ERROR";

// =====================================================

namespace pilecv4j
{
namespace ffmpeg
{

const char* errMessage(uint64_t status) {
  // if the MSBs have a value, then that's what we're going with.
  {
    uint32_t pcv4jCode = (status >> 32) & 0xffffffff;
    if (pcv4jCode != 0) {
      if (pcv4jCode < 0 || pcv4jCode > MAX_PCV4J_CODE)
        return strdup(totallyUnknownError);
      else
        return strdup(pcv4jStatMessages[pcv4jCode]);
    }
  }
  char* ret = new char[AV_ERROR_MAX_STRING_SIZE + 1]{0};
  av_strerror((int)status, ret, AV_ERROR_MAX_STRING_SIZE);
  return ret;
}

std::string removeOption(const std::string& key, std::vector<std::tuple<std::string,std::string> >& options) {
  std::string ret;
  for (auto iter = options.begin(); iter != options.end(); ++iter) {
    std::tuple<std::string,std::string> cur = *iter;
    if (key == std::get<0>(cur)) {
      ret = std::get<1>(cur);
      options.erase(iter);
      return ret;
    }
  }
  return ret;
}

uint64_t buildOptions(const std::vector<std::tuple<std::string,std::string> >& options, AVDictionary** opts) {
  if (options.size() == 0) {
    if (isEnabled(TRACE))
      log(TRACE,"UTIL","No options set. Setting opts to nullptr");
    *opts = nullptr;
    return 0;
  }

  for (auto o : options) {
    uint64_t result = MAKE_AV_STAT(av_dict_set(opts, std::get<0>(o).c_str(), std::get<1>(o).c_str(), 0 ));
    if (isError(result)) {
      if (*opts) {
        av_dict_free(opts);
        *opts = nullptr;
      }
      return result;
    }
  }
  return 0;
}

uint64_t buildOptions(const std::map<std::string,std::string>& options, AVDictionary** opts) {
  *opts = nullptr;
  if (options.size() == 0)
    return 0;

  uint64_t result = 0;
  for (std::map<std::string, std::string>::const_iterator it = options.begin(); it != options.end(); it++) {
    if (!it->second.empty() && !it->first.empty()) {
      result = MAKE_AV_STAT(av_dict_set(opts, it->first.c_str(), it->second.c_str(), 0));
      if (isError(result)) {
        if (*opts) {
          av_dict_free(opts);
          *opts = nullptr;
        }
        return result;
      }
    }
  }

  return 0;
}

void rebuildOptions(const AVDictionary* opts, std::map<std::string,std::string>& result) {
  char* entries = nullptr;
  AVDictionaryEntry* entry = NULL;

  result.clear();
  // iterate through each entry in the dictionary
  while ((entry = av_dict_get(opts, "", entry, AV_DICT_IGNORE_SUFFIX))) {
    result[entry->key] = entry->value;
  }
}

void rebuildOptions(const AVDictionary* opts, std::vector<std::tuple<std::string,std::string> >& result) {
  char* entries = nullptr;
  AVDictionaryEntry* entry = NULL;

  result.clear();
  // iterate through each entry in the dictionary
  while ((entry = av_dict_get(opts, "", entry, AV_DICT_IGNORE_SUFFIX))) {
    result.push_back(std::tuple<std::string, std::string>(entry->key, entry->value));
  }
}

void logRemainingOptions(LogLevel logLevel, const char* component, const char* header,
                                       const std::vector<std::tuple<std::string,std::string> >& options) {
  if (options.size() == 0) {
    log(logLevel, component, "All options were used. No remaining options %s", header);
    return;
  }

  log(logLevel, component, "Remaining options %s:", header);
  for (auto o : options) {
    log(logLevel, component, "  %s = %s", std::get<0>(o).c_str(), std::get<1>(o).c_str());
  }
}

void logRemainingOptions(LogLevel logLevel, const char* component, const char* header,
                                       const std::map<std::string,std::string>& options) {
  if (options.size() == 0) {
    log(logLevel, component, "All options were used. No remaining options %s", header);
    return;
  }

  log(logLevel, component, "Remaining options %s:", header);
  for (std::map<std::string, std::string>::const_iterator it = options.begin(); it != options.end(); it++) {
    log(logLevel, component, "  %s = %s",  it->first.c_str(), it->second.c_str());
  }
}

bool decoderExists(AVCodecID id) {
  // finds the registered decoder for a codec ID
  // https://ffmpeg.org/doxygen/trunk/group__lavc__decoding.html#ga19a0ca553277f019dd5b0fec6e1f9dca
  const AVCodec *pLocalCodec = safe_find_decoder(id);
  if (!pLocalCodec)
    log(WARN, "UTIL", "ERROR unsupported codec (%d)!", id);

  return pLocalCodec != nullptr;
}

extern "C" {

  // ===========================================================
  // Status code handling
  // ===========================================================

  // use pcv4j_ffmpeg_freeString to free the string that's returned
  KAI_EXPORT const char* pcv4j_ffmpeg2_utils_statusMessage(uint64_t status) {
    return errMessage(status);
  }

  // free the string returned from pcv4j_ffmpeg_statusMessage
  KAI_EXPORT void pcv4j_ffmpeg2_utils_freeString(char* str) {
    if (str)
      delete[] str;
  }

  KAI_EXPORT int32_t pcv4j_ffmpeg2_utils_isGpuAvailable() {
      AVHWDeviceType type = AV_HWDEVICE_TYPE_NONE;
      while ((type = av_hwdevice_iterate_types(type)) != AV_HWDEVICE_TYPE_NONE) {
          AVBufferRef *hw_device_ctx = nullptr;
          if (av_hwdevice_ctx_create(&hw_device_ctx, type, nullptr, nullptr, 0) == 0) {
              av_buffer_unref(&hw_device_ctx);
              return 1;
          }
      }
      return 0;
  }
}

}
}


