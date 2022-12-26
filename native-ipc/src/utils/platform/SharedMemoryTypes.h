#pragma once

#if IPC_TYPE == IPC_TYPE_windows
#include "windows/SharedMemoryTypes.h"
#elif IPC_TYPE == IPC_TYPE_posix
#include "posix/SharedMemoryTypes.h"
#else
#error "IPC_TYPE wasn't set."
#endif
