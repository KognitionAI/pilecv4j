#include "utils/platform/SharedMemoryImpl.h"

#include "utils/platform/errHandlingImpl.h"
#include "utils/log.h"

#include <bits/stdc++.h>

// see the man page for shmget for why it's safer to include all 3 of these.
#include  <sys/types.h>
#include  <sys/ipc.h>
#include  <sys/shm.h>

namespace pilecv4j {
namespace ipc {

static const char* implName = "SystemV";

#define COMPONENT "SMSV"

class SharedMemorySystemVImpl : public SharedMemoryImpl {
public:
  inline SharedMemorySystemVImpl(const char* name, int32_t nameRep) : SharedMemoryImpl(name, nameRep) {}
  virtual ~SharedMemorySystemVImpl() = default;
  inline virtual bool implementationRequiresCreatorToBeOwner() { return true; }

};

const char* SharedMemory::implementationName() {
  return implName;
}

bool SharedMemoryImpl::createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep, std::size_t size) {
  *fd = shmget(
      (key_t)nameRep,        /* the key for the segment         */
      (size_t)size,          /* the size of the segment         */
      IPC_CREAT | 0666);     /* create/use flag                 */

  int err;
  const char* errPrefix = "";
  if (*fd < 0) {
    err = errno;
    if (err == EINVAL) {
      // assume the memory segment is there and is small so lets see if we can delete it.
      int tmpshmid = shmget(nameRep, 1, 0666);
      if (tmpshmid < 0) {
        errPrefix = "shmget";
        goto recover_error;
      }

      if (shmctl(tmpshmid, IPC_RMID, nullptr)) {
        errPrefix = "shmctl";
        goto recover_error;
      }

      // otherwise we're set to try again ....
      *fd = shmget(
          (key_t)nameRep,        /* the key for the segment         */
          (size_t)size,          /* the size of the segment         */
          IPC_CREAT | 0666);     /* create/use flag                 */

    }
  }

  return (*fd < 0) ? false : true;

  recover_error:
  {
    // well, that failed.
    int terr = errno;
    std::string tmpErrMsg = getErrorMessage((ErrnoType)terr);
    log(ERROR, COMPONENT, "Couldn't create the shared memory (0x%08x) segment and couldn't %s one that might be in the way. Error %d, %s", (int)nameRep, errPrefix, terr, tmpErrMsg.c_str());
    errno = err;
    return false;
  }
}

bool SharedMemoryImpl::openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep) {
  // we're going to have to get this twice since we need to read the size from the header.
  int shmid = shmget(
      (key_t)nameRep,        /* the key for the segment         */
      sizeof(Header),        /* the size of the segment         */
      0666);                 /* create/use flag                 */

  if (shmid < 0)
    return false;

  // otherwise we need to read the header.
  void* caddr = shmat(shmid, nullptr, SHM_RDONLY);
  if (caddr == (void*)-1)
    return false;

  Header* header = (Header*)caddr;
  if (header->magic != PILECV4J_SHM_HEADER_MAGIC) {
    errno = ENOENT; // set this to it doesn't exist yet.
    return false;
  }

  size_t size = header->totalSize;
  // okay, now detach
  if (shmdt(caddr))
    // this is a major problem. I should log it but I don't want errno to be reset before it's deciphered by the caller
    return false;

  // remap
  *fd = shmget(
      (key_t)nameRep,        /* the key for the segment         */
      size,                  /* the size of the segment         */
      0666);                 /* create/use flag                 */

  if (*fd < 0)
    return false;

  return true;
}

bool SharedMemoryImpl::mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t size) {
  *addr = shmat(fd, NULL, 0);
  return (*addr != (void*)-1);
}

bool SharedMemoryImpl::unmmapSharedMemorySegment(void* addr, std::size_t size) {
  return shmdt(addr) ? false : true;
}

bool SharedMemoryImpl::closeSharedMemorySegment(SharedMemoryDescriptor fd, const char* name, int32_t nameRep) {
  return !shmctl(fd, IPC_RMID, nullptr);
}

SharedMemory* SharedMemory::instantiate(const char* name, int32_t nameRep) {
  return new SharedMemorySystemVImpl(name, nameRep);
}

}
}
