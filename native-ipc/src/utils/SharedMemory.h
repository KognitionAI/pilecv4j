#pragma once

#include <string>

#include "utils/log.h"

// forward declare mat
namespace cv {
class Mat;
}

namespace pilecv4j {
namespace ipc {

class SharedMemory {
  std::string name;
  std::size_t dataOffset;
  std::size_t totalSize;
  int fd;
  void* addr;
  void* data;
  bool isOpen;
  bool owner;

public:
  inline SharedMemory(const char* pname) : dataOffset(-1), totalSize(-1), fd(-1), addr(nullptr),
                                       data(nullptr), isOpen(false), owner(false) {
    if (pname)
      name = pname;
  }

  virtual ~SharedMemory();

  uint64_t create(std::size_t numBytes, bool owner);
  uint64_t open(bool owner);

  uint64_t getBufferSize(std::size_t& out);
  uint64_t getBuffer(std::size_t offset, void*& out);

  // set message available to be read
  uint64_t postMessage();

  // mark message as having been read.
  uint64_t unpostMessage();

  // you should have the lock when calling this.
  uint64_t isMessageAvailable(bool& available);

  /**
   * Returns EAGAIN if the the timeout occurs before the lock is obtained.
   * aggressive means use "yield()" rather than "sleep_for(1ms)" between
   * polling iterations.
   */
  uint64_t lock(int64_t millisecondsToWait = -1, bool aggressive = false);

  inline uint64_t tryLock() {
    return lock(0, false);
  }

  /**
   * The process must have the lock or the results
   * will be unknown
   */
  uint64_t unlock();

};

template <typename T>
static inline T align64(T x) {
  return (x & (T)0x1f) ? (((x >> 5) + 1) << 5) : x;
}

}
}

#undef COMPONENT

