#include "utils/platform/errno/errHandlingImpl.h"

#include <string.h>

namespace pilecv4j {
namespace ipc {

ErrnoType getLastError() {
  ErrnoType err = errno;
  return err;
}

std::string getErrorMessage(ErrnoType errorMessageID)
{
  int err = errno;
  char erroStr[256];
  const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
  erroStr[sizeof(erroStr) - 1] = (char)0;
  std::string ret(msg);
  return ret;
}

}
}

