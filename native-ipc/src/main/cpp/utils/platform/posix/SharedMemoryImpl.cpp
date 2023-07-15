#include "utils/platform/SharedMemoryImpl.h"

#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>

#include "utils/log.h"
#include <inttypes.h>
#include <sys/stat.h>

#define COMPONENT "POSI"
#define PCV4K_IPC_TRACE RAW_PCV4J_IPC_TRACE(COMPONENT)

namespace pilecv4j {
namespace ipc {

static const char* implName = "Posix";

const char* SharedMemory::implementationName() {
  return implName;
}

bool SharedMemoryImpl::createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep, std::size_t size) {
  PCV4K_IPC_TRACE;
  *fd = shm_open(name, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);// <----------+
  log(TRACE, COMPONENT, "opened shm and received a fd: %d", (int)(*fd));
  if (*fd == -1)                                       //                 |
    return false;                                      //                 |
  //       There is a race condition here which is "fixed" with a STUPID hack.
  //       The other process can open the shm segment now and mmap it before
  //       this process ftruncates it in which case access to the mapped memory
  //       will cause a seg fault. Therefore there's a stupid SLEEP in there
  //  +--- to minimize this gap.
  //  |
  //  v
  log(TRACE, COMPONENT, "truncating shm fd %d to %ld", (int)(*fd), (long)size);
  if (ftruncate(*fd, size) == -1)
    return false;
  {
    struct stat file_stat;
    if (fstat(*fd, &file_stat) == -1) {
      log(ERROR, COMPONENT, "Failed to stat the newly ftruncated mmap file descriptor: fd %d of requested size %ld", (int)(*fd), (long)size);
      return false;
    }
    log(TRACE, COMPONENT, "ftruncated file of requested size %ld is actually %ld", (long)size, (long)(file_stat.st_size));
  }
  return true;
}

bool SharedMemoryImpl::openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep) {
  PCV4K_IPC_TRACE;
  *fd = shm_open(name, O_RDWR, S_IRUSR | S_IWUSR);
  return (*fd != -1);
}

bool SharedMemoryImpl::mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t size) {
  PCV4K_IPC_TRACE;
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "mmap fd %d of size %ld", (int)fd, (long)size);
  *addr = mmap(NULL, size, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "mmap completed 0x%" PRIx64, (uint64_t)addr);
  return (*addr != MAP_FAILED);
}

bool SharedMemoryImpl::unmmapSharedMemorySegment(void* addr, std::size_t size) {
  PCV4K_IPC_TRACE;
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "munmap addr 0x%" PRIx64 " of size %ld", (uint64_t)addr, (long)size);
  return munmap(addr, size) != -1;
}

bool SharedMemoryImpl::closeSharedMemorySegment(SharedMemoryDescriptor fd, const char* name, int32_t nameRep) {
  PCV4K_IPC_TRACE;
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "shm_unlink fd %d, %s", (int)fd, name);
  return !shm_unlink(name);
}

SharedMemory* SharedMemory::instantiate(const char* name, int32_t nameRep) {
  PCV4K_IPC_TRACE;
  return new SharedMemoryImpl(name, nameRep);
}

}
}
