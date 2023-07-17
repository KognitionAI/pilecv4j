#include "utils/platform/SharedMemoryImpl.h"

#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>

#include "utils/log.h"
#include <inttypes.h>
#include <sys/stat.h>
#include <errno.h>
#include <string.h>

#define COMPONENT "POSI"
#define PCV4K_IPC_TRACE RAW_PCV4J_IPC_TRACE(COMPONENT)

namespace pilecv4j {
namespace ipc {

static const char* implName = "Posix";

static size_t pagesize = sysconf(_SC_PAGESIZE);

const char* SharedMemory::implementationName() {
  return implName;
}

bool SharedMemoryImpl::createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep, std::size_t psize) {
  PCV4K_IPC_TRACE;

  // we need to adjust the requested size to it's an even multiple of the page size
  std::size_t size;
  {
    size = ((psize % pagesize) != 0) ? (size_t)(((psize / pagesize) + 1) * pagesize) : psize;
    if (isEnabled(DEBUG))
      log(DEBUG, COMPONENT, "The total size was adjusted to %ld bytes from a requested size of %ld given a page size of %d",
          (long)size, (long)psize, (int)pagesize);
  }

  *fd = shm_open(name, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);// <-------------+
  if (*fd == -1)                                       //                    |
    return false;                                      //                    |
  //       There is a race condition here which is "fixed" with a STUPID hack.
  //       The other process can open the shm segment now and mmap it before
  //       this process ftruncates it in which case access to the mapped memory
  //       will cause a seg fault. Therefore there's a stupid SLEEP in there
  //  +--- to minimize this gap.
  //  |
  //  v
  if (ftruncate(*fd, size) == -1)
    return false;

  const bool trace = isEnabled(TRACE);

  if (trace) {
    log(TRACE, COMPONENT, "opened shm and received a fd: %d", (int)(*fd));
    log(TRACE, COMPONENT, "truncated shm fd %d to %ld", (int)(*fd), (long)size);
  }

  {
    struct stat file_stat;
    if (fstat(*fd, &file_stat) == -1) {
      log(ERROR, COMPONENT, "Failed to stat the newly ftruncated mmap file descriptor: fd %d of requested size %ld", (int)(*fd), (long)size);
      return false;
    }
    if (trace)
      log(TRACE, COMPONENT, "ftruncated file of requested size %ld is actually %ld", (long)size, (long)(file_stat.st_size));
  }
  return true;
}

bool SharedMemoryImpl::openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep) {
  PCV4K_IPC_TRACE;
  *fd = shm_open(name, O_RDWR, S_IRUSR | S_IWUSR);
  return (*fd != -1);
}

bool SharedMemoryImpl::mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t psize) {
  PCV4K_IPC_TRACE;

  const bool trace = isEnabled(TRACE);

  // we need to adjust the requested size to it's an even multiple of the page size
  std::size_t size;
  {
    size = ((psize % pagesize) != 0) ? (size_t)(((psize / pagesize) + 1) * pagesize) : psize;
    if (isEnabled(DEBUG))
      log(DEBUG, COMPONENT, "For mmap, the total size was adjusted to %ld bytes from a requested size of %ld given a page size of %d",
          (long)size, (long)psize, (int)pagesize);
  }

  if (trace)
    log(TRACE, COMPONENT, "mmap fd %d of size %ld", (int)fd, (long)size);
  *addr = mmap(NULL, size, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
  if (trace)
    log(TRACE, COMPONENT, "mmap completed 0x%" PRIx64, (uint64_t)addr);
  return (*addr != MAP_FAILED);
}

bool SharedMemoryImpl::unmmapSharedMemorySegment(void* addr, std::size_t size, const bool closing) {
  PCV4K_IPC_TRACE;
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "munmap addr 0x%" PRIx64 " of size %ld", (uint64_t)addr, (long)size);
  // we need to close the fd.
  if (closing) {
    if (close(fd) != 0) {
      log(ERROR, COMPONENT, "Failed to close the file descriptor %d: %s", (int)fd, strerror(errno));
      return false;
    }
  }

  return munmap(addr, size) != -1;
}

bool SharedMemoryImpl::unlinkSharedMemorySegment(SharedMemoryDescriptor fd, const char* name, int32_t nameRep) {
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
