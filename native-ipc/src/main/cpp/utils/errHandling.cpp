
#include "errHandling.h"
#include "common/kog_exports.h"

#include <string.h>
#include <errno.h>

namespace pilecv4j {
namespace ipc {

/**
 * errCode == 0 is SUCCESS.
 * If the MSBs are clear then the errorCode just holds an errno
 *
 */
static const char* ok = "SUCCESS";
static const char* unknown = "Unknown Error Code";

#define MSB_MASK ((uint64_t)0xffffffff00000000)
#define MAX_ERR_STRING_LEN 256

static const char* errStrings[] = {
    "Success",
    "Invalid State",
    "Null reference passed.",
    "Shared memory is not opened. You need to create or open it first.",
    "This implementation of shared memory requires the creator to be the owner",
    "The shared memory is already open."
};
#define MAX_ERR_INDEX 5

const char* errString(uint64_t errorCode) {
  if (!errorCode) return ok;
  char* ret = new char[MAX_ERR_STRING_LEN];
  if ((MSB_MASK & errorCode) == 0) {
    // we're just an errno (or windows error - like Bill Gates going to Epstein island).
    std::string errMsgStr = getErrorMessage((ErrnoType)errorCode);
    strncpy(ret, errMsgStr.c_str(), MAX_ERR_STRING_LEN);
    return ret;
  } else {
    // extract the LSB
    int code = (int)(errorCode & ~(MSB_MASK));
    const char* errStr = (code > MAX_ERR_INDEX) ? unknown : errStrings[code];
    strncpy(ret, errStr, MAX_ERR_STRING_LEN);
    ret[MAX_ERR_STRING_LEN - 1] = (char)0; // more belt and suspenders
    return ret;
  }
}

void freeErrString(char* errStr) {
  if (errStr && errStr != ok)
    delete[] errStr;
}

extern "C" {
KAI_EXPORT const char* pcv4j_ipc_errHandling_errString(uint64_t errCode) {
  return errString(errCode);
}

KAI_EXPORT void pcv4j_ipc_errHandling_freeErrString(char* str) {
  freeErrString(str);
}

KAI_EXPORT uint64_t pcv4j_ipc_errHandling_getEAGAIN() {
  return fromErrno(EAGAIN);
}

KAI_EXPORT uint64_t pcv4j_ipc_errHandling_getOK() {
  return fromErrorCode(OK);
}

}
}
}
