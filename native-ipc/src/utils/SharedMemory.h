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

  inline bool isOwner() {
    return owner;
  }

  uint64_t create(std::size_t numBytes, bool owner, std::size_t numMailboxes);
  uint64_t open(bool owner);

  uint64_t getBufferSize(std::size_t& out);
  uint64_t getBuffer(std::size_t offset, void*& out);

  // set message available to be read. Lock should already be held.
  uint64_t postMessage(std::size_t mailbox);

  // mark message as having been read. Lock should already be held.
  uint64_t unpostMessage(std::size_t mailbox);

  /**
   * Checks if a message is available to be read.
   */
  uint64_t isMessageAvailable(bool& available, std::size_t mailbox);

  /**
   * Checks if space for a message is available.
   */
  inline uint64_t canWriteMessage(bool& canWrite, std::size_t mailbox) {
    bool available;
    uint64_t ret = isMessageAvailable(available, mailbox);
    canWrite = !available;
    return ret;
  }

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

  /**
   * Explicitly unlink the shared memory segment if it's not already unlinked.
   * This will be done automatically in the destructor if we are the owner and
   * if not done explicitly prior to that.
   */
  uint64_t unlink();

};

template <typename T>
static inline T align64(T x) {
  return (x & (T)63) ? (((x >> 6) + 1) << 6) : x;
}

}
}

#undef COMPONENT

