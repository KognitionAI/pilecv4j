#ifndef _PILECV4J_UTILS__
#define _PILECV4J_UTILS__

#include <string>
#include <cstdarg>
#include <vector>

inline std::string StringFormat(const std::string& format, ...)
{
    va_list args;
    va_start (args, format);
    size_t len = std::vsnprintf(NULL, 0, format.c_str(), args);
    va_end (args);
    std::vector<char> vec(len + 1);
    va_start (args, format);
    std::vsnprintf(&vec[0], len + 1, format.c_str(), args);
    va_end (args);
    return &vec[0];
}

#endif
