#pragma once

#include <windows.h>
#include <string>

namespace pilecv4j {
namespace ipc {

typedef DWORD ErrnoType;

ErrnoType getLastError();
std::string getErrorMessage(ErrnoType err);

}
}
