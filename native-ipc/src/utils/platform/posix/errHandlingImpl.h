#pragma once

#include <string>

namespace pilecv4j {
namespace ipc {

typedef int ErrnoType;

ErrnoType getLastError();
std::string getErrorMessage(ErrnoType err);

}
}
