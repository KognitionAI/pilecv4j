#pragma once

#include <windows.h>
// The stupid ass windows #defined ERROR
#undef ERROR
#include <string>

namespace pilecv4j {
namespace ipc {

typedef DWORD ErrnoType;

ErrnoType getLastError();
std::string getErrorMessage(ErrnoType err);

}
}
