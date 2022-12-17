#pragma once

#include <string>

#include "utils/log.h"

// forward declare mat
namespace cv {
class Mat;
}

namespace pilecv4j {
namespace ipc {

class ShmQueue {
  std::string name;
  std::size_t dataOffset;
  std::size_t totalSize;
  int fd;
  void* addr;
  void* data;
  bool isOpen;
  bool owner;

public:
  inline ShmQueue(const char* pname) : dataOffset(-1), totalSize(-1), fd(-1), addr(nullptr),
                                       data(nullptr), isOpen(false), owner(false) {
    if (pname)
      name = pname;
  }

  virtual ~ShmQueue();

  bool create(std::size_t numBytes, bool owner);
  bool open(bool owner);

  std::size_t bufferSize();

  void* tryGetWriteView();
  bool markWritten();

  void* tryGetReadView();
  bool markRead();
};

template <typename T>
static inline T align64(T x) {
  return (x & (T)0x1f) ? (((x >> 5) + 1) << 5) : x;
}

}
}

#undef COMPONENT

