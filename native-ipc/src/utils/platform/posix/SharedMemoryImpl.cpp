#include "utils/platform/SharedMemoryImpl.h"

#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>

namespace pilecv4j {
namespace ipc {

bool SharedMemoryImpl::createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, std::size_t size) {
  *fd = shm_open(name, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR);// <-----------+
  if (*fd == -1)                                       //                 |
    return false;                                      //                 |
  //       There is a race condition here which is "fixed" with a STUPID hack.
  //       The other process can open the shm segment now and mmap it before
  //       this process ftruncates it in which case access to the mapped memory
  //       will cause a seg fault. Therefore there's a stupid SLEEP in there
  //  +--- to minimize this gap.
  //  |
  //  v
  if (ftruncate(*fd, size) == -1)
    return false;
  return true;
}

bool SharedMemoryImpl::openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name) {
  *fd = shm_open(name, O_RDWR, S_IRUSR | S_IWUSR);
  return (*fd != -1);
}

bool SharedMemoryImpl::mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t size) {
  *addr = mmap(NULL, size, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
  return (*addr != MAP_FAILED);
}

bool SharedMemoryImpl::unmmapSharedMemorySegment(void* addr, std::size_t size) {
  return munmap(addr, size) != -1;
}

bool SharedMemoryImpl::closeSharedMemorySegment(SharedMemoryDescriptor fd, const char* name) {
  return !shm_unlink(name);
}

SharedMemory* SharedMemory::instantiate(const char* name) {
  return new SharedMemoryImpl(name);
}

}
}
