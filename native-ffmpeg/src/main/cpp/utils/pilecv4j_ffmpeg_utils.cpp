
#include "kog_exports.h"
#include "utils/pilecv4j_ffmpeg_utils.h"

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
    "The stream selection failed."
};

/**
 * Error and status support.
 */
static const char* totallyUnknownError = "UNKNOWN ERROR";

// =====================================================

using namespace pilecv4j::ffmpeg;

extern "C" {

  // ===========================================================
  // Status code handling
  // ===========================================================

  // use pcv4j_ffmpeg_freeString to free the string that's returned
  KAI_EXPORT char* pcv4j_ffmpeg2_utils_statusMessage(uint64_t status) {
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

  // free the string returned from pcv4j_ffmpeg_statusMessage
  KAI_EXPORT void pcv4j_ffmpeg2_utils_freeString(char* str) {
    if (str)
      delete[] str;
  }
}

