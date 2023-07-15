#include "common/EndTime.h"
#include "common/kog_exports.h"

#include "utils/log.h"
#include "utils/errHandling.h"

#include <thread>

#ifdef LOCKING
#include <semaphore.h>
#endif

#include "utils/SharedMemory.h"

#include "utils/cvtypes.h"
#include <inttypes.h>

#define COMPONENT "SHMQ"
#define PCV4K_IPC_TRACE RAW_PCV4J_IPC_TRACE(COMPONENT)

// =========================================================
// shorthand for some ubiquitous checks
#ifdef NO_CHECKS
#define MAILBOX_CHECK(h, x)
#define OPEN_CHECK(x)
#define NULL_CHECK(nr)
#else
#define MAILBOX_CHECK(h, x) if (x >= h->numMailboxes) { \
  log(ERROR, COMPONENT, "There are only %d mailboxes. You referenced mailbox %d", (int)header->numMailboxes, x); \
  return fromErrno(EINVAL); \
}

#define OPEN_CHECK(x)   if (! m_isOpen) { \
    log(ERROR, COMPONENT, "Cannot " x " until the shm segment is open"); \
    return fromErrorCode(NOT_OPEN); \
  }

#define NULL_CHECK(nr) if (nr == 0) { \
  log(ERROR, COMPONENT, "Null ShmQueue native reference passed"); \
  return fromErrorCode(NULL_REF); \
}
#endif
// =========================================================

using namespace std::chrono_literals;
using namespace ai::kognition::pilecv4j;

namespace pilecv4j {
namespace ipc {

static const uint64_t OK_RET = fromErrorCode(OK);

#define std_atomic_fence(x) std::atomic_thread_fence(x)

#ifdef LOCKING
static uint64_t lockMe(sem_t* sem, int64_t millis, bool aggressive) {
  PCV4K_IPC_TRACE;
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
  PCV4K_IPC_TRACE;
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
#else
#define unlockMe(x) OK_RET
#define lockMe(x,y,z) OK_RET
#endif

void SharedMemory::cleanup() {
  if (addr && totalSize >= 0) {
    // if we're the owner, and we're going to unlink this, then before we
    // unmap the shared memory, we want to set the magic number to a bogus
    // value.
    if (owner && fd > 0) { // the means we're going to unlink it below.
      Header* header = (Header*)addr;
      header->magic = 0x0;
    }
    if (!unmmapSharedMemorySegment(addr, totalSize)) {
      ErrnoType err = getLastError();
      std::string errMsgStr = getErrorMessage(err);
      log(ERROR, COMPONENT, "Failed to un-mmap the shared memory segment. Error %d: %s", (int)err, errMsgStr.c_str());
    }
    addr = nullptr;
    totalSize = -1;
  }
  if (owner && fd >= 0) {
    unlink();
  }
}

uint64_t SharedMemory::unlink() {
  if (isEnabled(TRACE))
    log(TRACE,COMPONENT,"unlinking the shared memory segment %s.", name.c_str());

  if (fd < 0 || !m_isOpen) {
    log(ERROR, COMPONENT, "Attempt to unlink the shared memory segment \"%s\" but it's not currently open", name.c_str());
    return fromErrorCode(NOT_OPEN);
  }

  if (!owner)
    log(WARN, COMPONENT, "unlinking the shared memory segment \"%s\" though I'm not the owner.", name.c_str());

  if (!closeSharedMemorySegment(fd, name.c_str(), nameRep)) {
    ErrnoType err = getLastError();
    std::string errMsgStr = getErrorMessage(err);
    log(ERROR, COMPONENT, "Failed to close the shared memory segment. Error %d: %s", err, errMsgStr.c_str());
    return fromErrno(err);
  }

  fd = PCV4J_IPC_DEFAULT_DESCRIPTOR;
  m_isOpen = false;

  return OK_RET;
}

uint64_t SharedMemory::create(std::size_t numBytes, bool powner, std::size_t numMailboxes) {
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "Creating shared mem queue for %ld bytes. Owner: %s", (long)numBytes, powner ? "true": "false" );

  if (!powner && implementationRequiresCreatorToBeOwner()) {
    log(ERROR, COMPONENT, "The implementation (%s) requires the creator to also be the owner.", implementationName());
    return fromErrorCode(CREATOR_MUST_BE_OWNER);
  }

  Header* hptr;
  std::string errMsgPrefix;
  std::size_t offsetToBuffer;

  // we need to round UP to the nearest 64 byte boundary
  offsetToBuffer = align64(sizeof(Header) + (numMailboxes * sizeof(std::size_t)));

  totalSize = align64(numBytes + offsetToBuffer);
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "  the total size including the header is %ld bytes with an offset of %d", (long)totalSize, (int)offsetToBuffer);

  if (!createSharedMemorySegment(&fd, name.c_str(), nameRep, totalSize)) {
    errMsgPrefix = "Failed to create shared memory segment";
    goto error;
  }

  this->owner = powner;

  if (!mmapSharedMemorySegment(&addr, fd, totalSize)) {
    log(ERROR, COMPONENT, "Failed to map memory segment");
    errMsgPrefix = "Failed to map memory segment";
    goto error;
  }

  log(TRACE, COMPONENT, "Casting addr 0x%" PRIx64 " of size %ld to Header", (uint64_t)addr, (long)totalSize);
  hptr = (Header*)addr;
#ifdef LOCKING
  if (sem_init(&(hptr->sem), 1, 1) == -1) {
    errMsg = "Failed to init semaphore";
    goto error;
  }
#endif
  log(TRACE, COMPONENT, "Clearing addr 0x%" PRIx64 " of size %ld", (uint64_t)addr, (long)totalSize);
  memset(addr,0,totalSize);
  log(TRACE, COMPONENT, "Clearing magic number at Header base 0x%" PRIx64 " field 'magic' at 0x%" PRIx64, (uint64_t)hptr, (uint64_t)(&(hptr->magic)));
  hptr->magic = 0L; // in case this is being reopened

  // set the sizes
  log(TRACE, COMPONENT, "Setting totalSize at Header base 0x%" PRIx64 " field 'totalSize' at 0x%" PRIx64, (uint64_t)hptr, (uint64_t)(&(hptr->totalSize)));
  hptr->totalSize = totalSize;
  log(TRACE, COMPONENT, "Setting numBytes at Header base 0x%" PRIx64 " field 'numBytes' at 0x%" PRIx64, (uint64_t)hptr, (uint64_t)(&(hptr->numBytes)));
  hptr->numBytes = numBytes;
  log(TRACE, COMPONENT, "Setting offset at Header base 0x%" PRIx64 " field 'offset' at 0x%" PRIx64, (uint64_t)hptr, (uint64_t)(&(hptr->offset)));
  hptr->offset = offsetToBuffer;
  log(TRACE, COMPONENT, "Setting numMailboxes at Header base 0x%" PRIx64 " field 'numMailboxes' at 0x%" PRIx64, (uint64_t)hptr, (uint64_t)(&(hptr->numMailboxes)));
  hptr->numMailboxes = numMailboxes;
  for (std::size_t i = 0; i < numMailboxes; i++) {
    log(TRACE, COMPONENT, "Clearing mailbox %d at Header base 0x%" PRIx64 " field 'messageAvailable[%d]' at 0x%" PRIx64, (int)i, (uint64_t)hptr, (int)i, (uint64_t)(&(hptr->messageAvailable[i])));
    hptr->messageAvailable[i] = 0;
  }

  data = ((uint8_t*)addr) + offsetToBuffer;
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "Allocated shared mem at 0x%p with offset to data of %d bytes putting the data at 0x%p", addr, (int)offsetToBuffer, data);

  // don't reorder any write operation below this with any read/write operations above this line
  std::atomic_thread_fence(std::memory_order_release);
  // set the magic number
  hptr->magic = PILECV4J_SHM_HEADER_MAGIC;
  this->m_isOpen = true;
  return OK_RET;

  error:
  {
    ErrnoType err = getLastError();
    std::string errMsgStr = getErrorMessage(err);
    log(ERROR, COMPONENT, "%s Error %d: %s", errMsgPrefix.c_str(), err, errMsgStr.c_str());
    return fromErrno(err);
  }
  return false;
}

uint64_t SharedMemory::open(bool powner) {

  if (isEnabled(TRACE) && logOpen)
    log(INFO, COMPONENT, "Attempting to open the shared memory segment: %s.", name.c_str());
  logOpen = false;

  if (m_isOpen)
    return fromErrorCode(ALREADY_OPEN);

  Header lheader;
  Header* header;
  EndTime<> endTime;
  ErrnoType err;
  void* tmpptr = nullptr;
  const char* errMsgPrefix = "";
  bool closeSm = false;

  if (!openSharedMemorySegment(&fd, name.c_str(), nameRep)) {
      err = getLastError();
      // no such file is a normal condition. This error code is the same on windows
      //    as it is on real OSes and is the same for posix and systemv
      if (err == ENOENT)
          return fromErrno(EAGAIN);
      errMsgPrefix = "Failed to open shared segment";
      goto error;
  }
  logOpen = true;
  closeSm = true;

  // see the above described race condition. We need to allow the other process enough
  // time to get from shm_open to ftruncate. Other than tests, open is called infrequently
  // so this shouldn't be an overall performance hit.
  std::this_thread::sleep_for(200ms);

  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Mapping the shared memory segment to read the header of %d bytes.", (int)sizeof(Header));
  if (!mmapSharedMemorySegment( &tmpptr, fd, sizeof(Header))) {
    err = getLastError();
    errMsgPrefix = "Failed to memory map header";
    goto error;
  }
  header = (Header*)tmpptr;

  this->owner = powner;

  // poll for the magic number to be set.
  endTime.set(500ms);
  while(header->magic != PILECV4J_SHM_HEADER_MAGIC && !endTime.isTimePast())
    std::this_thread::yield();

  if (header->magic != PILECV4J_SHM_HEADER_MAGIC) {
    if (isEnabled(DEBUG))
      log(DEBUG, COMPONENT, "Timed out waiting for the create side to setup the semaphore");

    // we want to close it since returning EAGAIN will likely cause the user
    // to retry opening it and we want to do if from scratch.
    if (!closeSharedMemorySegment(fd, name.c_str(), nameRep)) {
      ErrnoType tmpErr = getLastError();
      std::string tmpErrMsgStr = getErrorMessage(tmpErr);
      log(WARN, COMPONENT, "Failed to reclose the shared memory segment. Error %d: %s", tmpErr, tmpErrMsgStr.c_str());
    }
    return fromErrno(EAGAIN);
  }

  // don't reorder the read above this with any read/write below this
  std::atomic_thread_fence(std::memory_order_release);

  // okay, it's set up. re-map it to the correct size.
  lheader = *header; // copy the header

  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Unmapping the header to remap the segment for the total at %ld bytes", (long)lheader.totalSize);

  if (!unmmapSharedMemorySegment(header, sizeof(Header))) {
    err = getLastError();
    errMsgPrefix = "Failed to unmap previously mapped header";
    goto error;
  }

  // now remap.
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Remapping the header to remap the segment for the total at %ld bytes", (long)lheader.totalSize);

  if(!mmapSharedMemorySegment(&addr, fd, lheader.totalSize)) {
    err = getLastError();
    errMsgPrefix = "Failed to memory REmap total segment";
    goto error;
  }
  this->data = ((uint8_t*)this->addr) + lheader.offset;
  this->totalSize = lheader.totalSize;
  this->m_isOpen = true;

  return OK_RET;
  error:
  {
    ErrnoType err = getLastError();
    std::string errMsgStr = getErrorMessage(err);
    log(WARN, COMPONENT, "%s Error %d: %s", errMsgPrefix, err, errMsgStr.c_str());
    if (closeSm) {
      if (!closeSharedMemorySegment(fd, name.c_str(), nameRep)) {
        ErrnoType tmpErr = getLastError();
        std::string tmpErrMsgStr = getErrorMessage(tmpErr);
        log(WARN, COMPONENT, "Failed to reclose the shared memory segment. Error %d: %s", tmpErr, tmpErrMsgStr.c_str());
      }
    }
    return fromErrno(err);
  }
}

uint64_t SharedMemory::getBufferSize(std::size_t& out) {
  PCV4K_IPC_TRACE;
  OPEN_CHECK("get buffer size");

  Header* header = (Header*)addr;
  out = (std::size_t)header->numBytes;
  return OK_RET;
}

uint64_t SharedMemory::getBuffer(std::size_t offset, void*& out) {
  PCV4K_IPC_TRACE;
  OPEN_CHECK("get buffer");

  out = ((uint8_t*)this->data + offset);
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "getBuffer is returning buffer at 0x%p", out);
  return OK_RET;
}

uint64_t SharedMemory::postMessage(std::size_t mailbox) {
  PCV4K_IPC_TRACE;
  OPEN_CHECK("post a message");

  Header* header = (Header*)addr;
  MAILBOX_CHECK(header, mailbox);
  // don't reorder any write operation below this with any read/write operations above this line
  std_atomic_fence(std::memory_order_release);
  header->messageAvailable[mailbox] = 1;
  //log(INFO, COMPONENT, "post: Header mailbox addr: 0x%llx : %llu", (void*)(&(header->messageAvailable[mailbox])), header->messageAvailable[mailbox]);
  return OK_RET;
}

uint64_t SharedMemory::reset() {
  PCV4K_IPC_TRACE;
  OPEN_CHECK("reset shm segment");

  Header* hptr = (Header*)addr;
  std::size_t totalSize = hptr->totalSize;
  std::size_t numBytes = hptr->numBytes;
  std::size_t offsetToBuffer = hptr->offset;
  std::size_t numMailboxes = hptr->numMailboxes;

  // don't reorder any write operation below this with any read/write operations above this line
  std_atomic_fence(std::memory_order_release);

  memset(addr,0,totalSize);

  hptr->magic = 0L; // in case this is being reopened

  // set the sizes
  hptr->totalSize = totalSize;
  hptr->numBytes = numBytes;
  hptr->offset = offsetToBuffer;
  hptr->numMailboxes = numMailboxes;
  for (std::size_t i = 0; i < numMailboxes; i++)
    hptr->messageAvailable[i] = 0;

  data = ((uint8_t*)addr) + offsetToBuffer;
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "Allocated shared mem at 0x%p with offset to data of %d bytes putting the data at 0x%p", addr, (int)offsetToBuffer, data);

  // don't reorder any write operation below this with any read/write operations above this line
  std::atomic_thread_fence(std::memory_order_release);
  // set the magic number
  hptr->magic = PILECV4J_SHM_HEADER_MAGIC;

  return OK_RET;
}

uint64_t SharedMemory::unpostMessage(std::size_t mailbox) {
  PCV4K_IPC_TRACE;
  OPEN_CHECK("unpost a message");

  Header* header = (Header*)addr;
  MAILBOX_CHECK(header, mailbox);
  // don't reorder any write operation below this with any read/write operations above this line
  std_atomic_fence(std::memory_order_release);
  header->messageAvailable[mailbox] = 0;
  //log(INFO, COMPONENT, "unpost: Header mailbox addr: 0x%llx : %llu", (void*)(&(header->messageAvailable[mailbox])), header->messageAvailable[mailbox]);

  return OK_RET;
}

uint64_t SharedMemory::isMessageAvailable(bool& out, std::size_t mailbox) {
  PCV4K_IPC_TRACE;
  OPEN_CHECK("check if a message is available");

  Header* header = (Header*)addr;
  MAILBOX_CHECK(header, mailbox);

  //log(INFO, COMPONENT, "isMessageAvailable: Header mailbox addr: 0x%llx : %llu", (void*)(&(header->messageAvailable[mailbox])), header->messageAvailable[mailbox]);
  out = header->messageAvailable[mailbox] ? true : false;
  // don't reorder the read above this with any read/write below this
  std_atomic_fence(std::memory_order_acquire);
  return OK_RET;
}

uint64_t SharedMemory::lock(int64_t millis, bool aggressive) {
  OPEN_CHECK("lock the shared memory segment");

  Header* header = (Header*)addr;

  return lockMe(&(header->sem), millis, aggressive);
}

uint64_t SharedMemory::unlock() {
  OPEN_CHECK("unlock the shared memory segment");

  Header* header = (Header*)addr;

  return unlockMe(&(header->sem));
}

extern "C" {

KAI_EXPORT uint64_t pilecv4j_ipc_create_shmQueue(const char* name, int32_t nameRep) {
  PCV4K_IPC_TRACE;
  log(INFO, COMPONENT, "Instantiating a %s SharedMemory segment using (%s, 0x%08x)", SharedMemory::implementationName(), name, (int)nameRep);
  return (uint64_t)SharedMemory::instantiate(name, nameRep);
}

KAI_EXPORT void pilecv4j_ipc_destroy_shmQueue(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  if (nativeRef)
    delete (SharedMemory*)nativeRef;
}

KAI_EXPORT const char* pilecv4j_ipc_implementationName() {
  return SharedMemory::implementationName();
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_create(uint64_t nativeRef, uint64_t size, int32_t owner, int32_t numMailboxes) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->create(size, owner == 0 ? false : true, (std::size_t)numMailboxes);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_open(uint64_t nativeRef, int32_t owner) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->open(owner == 0 ? false : true);
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_isOpen(uint64_t nativeRef, int32_t* ret) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (!ret)
    return fromErrno(EINVAL);
  *ret = ((SharedMemory*)nativeRef)->isOpen() ? 1 : 0;
  return OK_RET;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_isOwner(uint64_t nativeRef, int32_t* ret) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (!ret)
    return fromErrno(EINVAL);
  *ret = ((SharedMemory*)nativeRef)->isOwner() ? 1 : 0;
  return OK_RET;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_unlink(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->unlink();
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
    log(TRACE, COMPONENT, "getting buffer and putting the results at 0x%p", ret);
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

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_isMessageAvailable(uint64_t nativeRef, int32_t* ret, int32_t mailbox) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (!ret)
    return fromErrno(EINVAL);
  bool isAvail = false;
  auto aret = (uint64_t)((SharedMemory*)nativeRef)->isMessageAvailable(isAvail, (std::size_t)mailbox);
  *ret = isAvail ? 1 : 0;
  return aret;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_canWriteMessage(uint64_t nativeRef, int32_t* ret, int32_t mailbox) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  if (!ret)
    return fromErrno(EINVAL);
  bool isAvail = false;
  auto aret = (uint64_t)((SharedMemory*)nativeRef)->canWriteMessage(isAvail, (std::size_t)mailbox);
  *ret = isAvail ? 1 : 0;
  return aret;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_postMessage(uint64_t nativeRef, int32_t mailbox) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  uint64_t ret = ((SharedMemory*)nativeRef)->postMessage((std::size_t)mailbox);
  return ret;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_reset(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  uint64_t ret = ((SharedMemory*)nativeRef)->reset();
  return ret;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_unpostMessage(uint64_t nativeRef, int32_t mailbox) {
  PCV4K_IPC_TRACE;
  NULL_CHECK(nativeRef);
  return ((SharedMemory*)nativeRef)->unpostMessage((std::size_t)mailbox);
}

KAI_EXPORT uint8_t pilecv4j_ipc_locking_isLockingEnabled() {
#ifdef LOCKING
  return (uint8_t)1;
#else
  return (uint8_t)0;
#endif
}

}
}
}

