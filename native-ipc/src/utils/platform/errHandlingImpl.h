#pragma once

#if ERR_HANDLING == ERR_HANDLING_windows
#include "windows/errHandlingImpl.h"
#elif ERR_HANDLING == ERR_HANDLING_errno
#include "errno/errHandlingImpl.h"
#else
#error "ERR_HANDLING wasn't set."
#endif
