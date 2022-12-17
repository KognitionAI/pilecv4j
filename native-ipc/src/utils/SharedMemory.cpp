#include "common/EndTime.h"
#include "common/kog_exports.h"

#include "utils/errHandling.h"

#include <thread>

#include <sys/mman.h>
#include <fcntl.h>
#include <semaphore.h>
#include <unistd.h>
#include <utils/SharedMemory.h>

#include "utils/cvtypes.h"


#define COMPONENT "SHMQ"
#define PCV4K_IPC_TRACE RAW_PCV4J_IPC_TRACE(COMPONENT)

using namespace std::chrono_literals;
using namespace ai::kognition::pilecv4j;

namespace pilecv4j {
namespace ipc {

static uint64_t OK_RET = fromErrorCode(OK);

#define SHM_HEADER_MAGIC 0xBADFADE0CAFEF00D
struct Header {
  uint64_t magic = SHM_HEADER_MAGIC;
  sem_t sem;
  std::size_t totalSize = 0;
  std::size_t numBytes = 0;
  std::size_t offset = 0;
  std::size_t messageAvailable = 0;
};


SharedMemory::~SharedMemory() {
  if (addr && totalSize >= 0) {
    if (munmap(addr, totalSize) == -1) {
      int err = errno;
      char erroStr[256];
      const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
      erroStr[sizeof(erroStr) - 1] = (char)0;
      log(ERROR, COMPONENT, "Failed to un-mmap the shared memory segment. Error %d: %s", err, msg);
    }
  }
  if (owner && fd >= 0) {
    if (shm_unlink(name.c_str())) {
      int err = errno;
      char erroStr[256];
      const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
      erroStr[sizeof(erroStr) - 1] = (char)0;
      log(ERROR, COMPONENT, "Failed to unlink the shared memory segment. Error %d: %s", err, msg);
    }
  }
}

uint64_t SharedMemory::create(std::size_t numBytes, bool powner) {
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "Creating shared mem queue for %ld bytes. Owner: %s", (long)numBytes, powner ? "true": "false" );

  Header* hptr;
  std::string errMsg;

  fd = shm_open(name.c_str(), O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);
  if (fd == -1){
    errMsg = "Failed to open shared memory segment";
    goto error;
  }

  // we need to round UP to the nearest 64 byte boundary
  dataOffset = align64(sizeof(Header));

  totalSize = align64(numBytes + dataOffset);
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "  the total size including the header is %ld bytes with an offset of %d", (long)totalSize, (int)dataOffset);
  if (ftruncate(fd, totalSize) == -1)
    goto error;

  // map shared memory to process address space
  addr = mmap(NULL, totalSize, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
  if (addr == MAP_FAILED) {
    errMsg = "Failed to map memory segment";
    goto error;
  }
  this->owner = powner;

  hptr = (Header*)addr;
  if (sem_init(&(hptr->sem), 1, 1) == -1) {
    errMsg = "Failed to init semaphore";
    goto error;
  }

  // set the sizes
  hptr->totalSize = totalSize;
  hptr->numBytes = numBytes;
  hptr->offset = dataOffset;
  hptr->messageAvailable = 0; // false - no message available yet.

  data = ((uint8_t*)addr) + dataOffset;
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "Allocated shared mem at 0x%lx with offset to data of %d bytes putting the data at 0x%lx", (long)addr, (int)dataOffset, (long)data);

  // don't reorder writes across this line
  std::atomic_thread_fence(std::memory_order_release);
  // set the magic number
  hptr->magic = SHM_HEADER_MAGIC;
  this->isOpen = true;
  return OK_RET;

  error:
  {
    int err = errno;
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(ERROR, COMPONENT, "%s. Error %d: %s", errMsg.c_str(), err, msg);
    return fromErrno(err);
  }
  return false;
}

uint64_t SharedMemory::open(bool powner) {
  Header lheader;
  Header* header;
  EndTime<> endTime;
  int err;

  fd = shm_open(name.c_str(), O_RDWR, S_IRUSR | S_IWUSR);
  if (fd == -1) {
    err = errno;
    if (err == ENOENT) // no such file is a normal condition
      return fromErrno(EAGAIN);
    goto error;
  }

  // map just the header so we can see what the total size is.
  header = (Header*)mmap(nullptr, sizeof(Header), PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
  if (header == MAP_FAILED) {
    err = errno;
    goto error;
  }
  this->owner = powner;

  // poll for the magic number to be set.
  endTime.set(50ms);
  while(header->magic != SHM_HEADER_MAGIC && !endTime.isTimePast())
    std::this_thread::yield();

  // don't reorder reads across this.
  std::atomic_thread_fence(std::memory_order_acquire);

  if (header->magic != SHM_HEADER_MAGIC) {
    if (isEnabled(DEBUG))
      log(DEBUG, COMPONENT, "Timed out waiting for the serving size to setup the semaphore");
    return fromErrno(EAGAIN);
  }

  // don't reorder reads across this.
  std::atomic_thread_fence(std::memory_order_acquire);

  // okay, it's set up. re-map it to the correct size.
  lheader = *header; // copy the header
  if (munmap(header, sizeof(Header)) == -1) {
    err = errno;
    goto error;
  }

  // now remap.
  this->addr = mmap(NULL, lheader.totalSize, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
  if (this->addr == MAP_FAILED)
    goto error;
  this->dataOffset = lheader.offset;
  this->data = ((uint8_t*)this->addr) + this->dataOffset;
  this->totalSize = lheader.totalSize;
  this->isOpen = true;

  return OK_RET;
  error:
  {
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(WARN, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
    return fromErrno(err);
  }
}

uint64_t SharedMemory::getBufferSize(std::size_t& out) {
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get buffer size until the shm segment is open");
    return fromErrorCode(NOT_OPEN);
  }

  Header* header = (Header*)addr;
  out = (std::size_t)header->numBytes;
  return OK_RET;
}

uint64_t SharedMemory::getBuffer(std::size_t offset, void*& out) {
  PCV4K_IPC_TRACE;
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get buffer size until the shm segment is open");
    return fromErrorCode(NOT_OPEN);
  }

  out = this->data;
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "getBuffer is returning buffer at 0x%lx", (long)out);
  return OK_RET;
}

uint64_t SharedMemory::postMessage() {
  PCV4K_IPC_TRACE;
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get buffer size until the shm segment is open");
    return fromErrorCode(NOT_OPEN);
  }

  Header* header = (Header*)addr;
  std::atomic_thread_fence(std::memory_order_release);
  header->messageAvailable = 1;
  return OK_RET;
}

uint64_t SharedMemory::unpostMessage() {
  PCV4K_IPC_TRACE;
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get buffer size until the shm segment is open");
    return fromErrorCode(NOT_OPEN);
  }

  Header* header = (Header*)addr;
  std::atomic_thread_fence(std::memory_order_release);
  header->messageAvailable = 0;
  return OK_RET;
}

uint64_t SharedMemory::isMessageAvailable(bool& out) {
  PCV4K_IPC_TRACE;
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get buffer size until the shm segment is open");
    return fromErrorCode(NOT_OPEN);
  }

  Header* header = (Header*)addr;

  out = header->messageAvailable ? true : false;
  std::atomic_thread_fence(std::memory_order_acquire);
  return OK_RET;
}

static uint64_t lockMe(sem_t* sem, int64_t millis, bool aggressive) {
  // ===========================================
  // try lockWrite
  if (millis == 0) {
    // try once performance boost
    if (sem_trywait(sem) == -1) {
      // this is okay if errno is EAGAIN
      int err = errno;
      if (err != EAGAIN) {
        char erroStr[256];
        const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
        erroStr[sizeof(erroStr) - 1] = (char)0;
        log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
      }
      return fromErrno(err);
    } else
      return OK_RET;
  }
  // ===========================================

  // if it's not just a poll, set up a timeout (which may be infinite).
  EndTime<std::chrono::milliseconds> endTime;
  if (millis > 0)
    endTime.set(std::chrono::milliseconds(millis));
  else
    endTime.setInfinite();

  while(!endTime.isTimePast()) {
    if (sem_trywait(sem) == -1) {
      // this is okay if errno is EAGAIN
      int err = errno;
      if (err != EAGAIN) {
        char erroStr[256];
        const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
        erroStr[sizeof(erroStr) - 1] = (char)0;
        log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
        return fromErrno(err);
      } else { // EAGAIN
        if (aggressive)
          std::this_thread::yield();
        else
          std::this_thread::sleep_for(1ms);
      }
    } else
      return OK_RET;
  }
  // if we got here, we timed out.
  return fromErrno(EAGAIN);
}

static uint64_t unlockMe(sem_t* sem) {
  if (sem_post(sem) == -1) {
    int err = errno;
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
    return fromErrno(err);
  }

  return OK_RET;
}

uint64_t SharedMemory::lock(int64_t millis, bool aggressive) {
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot send a cv::Mat unless the MapQueue is opened first");
    return fromErrorCode(NOT_OPEN);
  }

  Header* header = (Header*)addr;

  return lockMe(&(header->sem), millis, aggressive);
}

uint64_t SharedMemory::unlock() {
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot send a cv::Mat unless the MapQueue is opened first");
    return fromErrorCode(NOT_OPEN);
  }

  Header* header = (Header*)addr;

  return unlockMe(&(header->sem));
}

extern "C" {

#define NULL_CHECK(nr) if (nr == 0) { \
  log(ERROR, COMPONENT, "Null ShmQueue native reference passed"); \
  return fromErrorCode(NULL_REF); \
}

KAI_EXPORT uint64_t pilecv4j_ipc_create_shmQueue(const char* name) {
  PCV4K_IPC_TRACE;
  return (uint64_t)new SharedMemory(name);
}

KAI_EXPORT void pilecv4j_ipc_destroy_shmQueue(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  if (nativeRef)
    delete (SharedMemory*)nativeRef;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_create(uint64_t nativeRef, uint64_t size, int32_t owner) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->create(size, owner == 0 ? false : true);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_open(uint64_t nativeRef, int32_t owner) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->open(owner == 0 ? false : true);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_bufferSize(uint64_t nativeRef, uint64_t* ret) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (!ret)
    return fromErrno(EINVAL);
  return (uint64_t)((SharedMemory*)nativeRef)->getBufferSize(*ret);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_buffer(uint64_t nativeRef, uint64_t offset, void** ret) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "getting buffer and putting the results at 0x%lx", (long)ret);
  if (!ret)
    return fromErrno(EINVAL);
  return (uint64_t)((SharedMemory*)nativeRef)->getBuffer((std::size_t)offset, *ret);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_lock(uint64_t nativeRef, int64_t millis, int32_t aggressive) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->lock(millis, aggressive == 0 ? false : true);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_unlock(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->unlock();
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_isMessageAvailable(uint64_t nativeRef, int32_t* ret) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (!ret)
    return fromErrno(EINVAL);
  bool isAvail = false;
  auto aret = (uint64_t)((SharedMemory*)nativeRef)->isMessageAvailable(isAvail);
  *ret = isAvail ? 1 : 0;
  return aret;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_postMessage(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->postMessage();
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_unpostMessage(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->unpostMessage();
}


}
}
}

