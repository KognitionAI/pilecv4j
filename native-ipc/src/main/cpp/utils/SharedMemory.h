#pragma once

#include "utils/errHandling.h"
#include "utils/platform/SharedMemoryTypes.h"

#include <string>

#include <stdint.h>

#define PILECV4J_SHM_HEADER_MAGIC 0xBADFADE0CAFEF00D

// forward declare mat
namespace cv {
class Mat;
}

namespace pilecv4j {
namespace ipc {

class SharedMemory {
protected:
  std::string name;
  int32_t nameRep;
  SharedMemoryDescriptor fd = PCV4J_IPC_DEFAULT_DESCRIPTOR;
  void* addr = nullptr;
  bool m_isOpen = false;
  bool owner = false;

  // these are just helpers but could be recalculated from the header.
  std::size_t totalSize = -1;
  void* data = nullptr;

  bool logOpen = true;

  // meant to be called from child destructors to avoid the problem with
  // calling [pure] virtual functions from a base class destructor.
  void cleanup();
public:
  inline SharedMemory(const char* pname, int32_t pnameRep) : nameRep(pnameRep) {
    if (pname)
      name = pname;
  }

  virtual ~SharedMemory() = default;

  // These are platform specific
  inline virtual bool implementationRequiresCreatorToBeOwner() { return false; }
  virtual bool createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep, std::size_t size) = 0;
  virtual bool openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep) = 0;
  virtual bool unlinkSharedMemorySegment(SharedMemoryDescriptor fd, const char* name, int32_t nameRep) = 0;
  virtual bool mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t size) = 0;
  virtual bool unmmapSharedMemorySegment(void* addr, std::size_t size) = 0;

  inline bool isOwner() {
    return owner;
  }

  inline bool isOpen() {
    return m_isOpen;
  }

  uint64_t create(std::size_t numBytes, bool owner, std::size_t numMailboxes);
  uint64_t open(bool owner);

  uint64_t getBufferSize(std::size_t& out);
  uint64_t getBuffer(std::size_t offset, void*& out);

  // set message available to be read. Lock should already be held.
  uint64_t postMessage(std::size_t mailbox);

  // mark message as having been read. Lock should already be held.
  uint64_t unpostMessage(std::size_t mailbox);

  uint64_t reset();

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

  /**
  * This will be implemented in the SharedMemoryImpl platform specific cpp file
  */
  static SharedMemory* instantiate(const char* name, int32_t nameRep);

  static const char* implementationName();

};

#ifdef _MSC_VER
// Yes. I KNOW the zero length array wont participate in a copy or initialized by
// a default constructor. Thanks Bill.
#pragma warning( push )
#pragma warning( disable : 4200 )
#endif
struct Header {
  uint64_t magic = PILECV4J_SHM_HEADER_MAGIC;
#ifdef LOCKING
  sem_t sem;
#endif
  std::size_t totalSize = 0;
  std::size_t numBytes = 0;
  std::size_t offset = 0;
  std::size_t numMailboxes = 0;
  std::size_t messageAvailable[0];
};
#ifdef _MSC_VER
#pragma warning( pop )
#endif

template <typename T>
static inline T align64(T x) {
  return (x & (T)63) ? (((x >> 6) + 1) << 6) : x;
}

}
}

#undef COMPONENT

