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
  int fd = -1;
  void* addr = nullptr;
  bool isOpen = false;
  bool owner = false;

  // these are just helpers but could be recalculated from the header.
  std::size_t totalSize = -1;
  void* data = nullptr;
public:
  inline SharedMemory(const char* pname) {
    if (pname)
      name = pname;
  }

  virtual ~SharedMemory();

  uint64_t create(std::size_t numBytes, bool owner, std::size_t numMailboxes);
  uint64_t open(bool owner);

  uint64_t getBufferSize(std::size_t& out);
  uint64_t getBuffer(std::size_t offset, void*& out);

  // set message available to be read
  uint64_t postMessage(std::size_t mailbox);

  // mark message as having been read.
  uint64_t unpostMessage(std::size_t mailbox);

  // you should have the lock when calling this.
  uint64_t isMessageAvailable(bool& available, std::size_t mailbox);

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

