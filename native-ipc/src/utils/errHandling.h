#pragma once

#include "utils/platform/SharedMemoryTypes.h"
#include "utils/platform/errHandlingImpl.h"

#include <stdint.h>

namespace pilecv4j {
namespace ipc {

/**
 * When the uint64_t errcode has the MSBs (the MS uint32_t) set to 1 then this is
 * the code in the LSBs (the LS uint32_t).
 *
 * DO NOT change the order here without fixing up the order of the strings in
 * the cpp file since these are using as indicies
 */
enum ErrorCode {
  OK = 0,
  INVALID_STATE = 1,
  NULL_REF = 2,
  NOT_OPEN = 3,
  CREATOR_MUST_BE_OWNER = 4,
  ALREADY_OPEN = 5
};

const char* errString(uint64_t errorCode);
void freeErrString(const char* errStr);

// DO NOT use the word `errno` as a variable
inline uint64_t fromErrno(ErrnoType en) {
  return (uint64_t)en;
}

inline uint64_t fromErrorCode(ErrorCode errorCode) {
  return ((uint64_t)0x0000000100000000L) | (uint64_t)errorCode;
}

}
}


