#include "utils/platform/SharedMemoryImpl.h"

namespace pilecv4j {
namespace ipc {

static const char* implName = "Windows";

const char* SharedMemory::implementationName() {
  return implName;
}

bool SharedMemoryImpl::createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep, std::size_t size) {
    // I'm assuming the size of a DWORD is 32-bits ... but windows sucks so who knows ...
    DWORD lowOrderTotalSize = (DWORD)((uint64_t)size & (uint64_t)0xffffffff);
    DWORD hiOrderTotalSize = (DWORD)(((uint64_t)size >> 32) & (uint64_t)0xffffffff);
    // need to open the shm segment ... windows style.
    *fd = CreateFileMapping(
        INVALID_HANDLE_VALUE,    // use paging file
        NULL,                    // default security
        PAGE_READWRITE,          // read/write access
        hiOrderTotalSize,        // maximum object size (high-order DWORD)
        lowOrderTotalSize,       // maximum object size (low-order DWORD)
        name);                   // name of mapping object

    return *fd != NULL;
}

bool SharedMemoryImpl::openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep) {
    *fd = OpenFileMapping(FILE_MAP_ALL_ACCESS, FALSE, name);
    return *fd != NULL;
}

bool SharedMemoryImpl::mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t size) {
  (*addr) = (void*)MapViewOfFile(fd, // handle to map object
      FILE_MAP_ALL_ACCESS,           // read/write permission
      0,
      0,
      size);
  return (addr != nullptr);
}

bool SharedMemoryImpl::unmmapSharedMemorySegment(void* addr, std::size_t size) {
    return UnmapViewOfFile(addr) != 0;
}

bool SharedMemoryImpl::closeSharedMemorySegment(SharedMemoryDescriptor fd, const char* name, int32_t nameRep) {
    return CloseHandle(fd) != 0;
}

SharedMemory* SharedMemory::instantiate(const char* name, int32_t nameRep) {
    return new SharedMemoryImpl(name, nameRep);
}

}
}
