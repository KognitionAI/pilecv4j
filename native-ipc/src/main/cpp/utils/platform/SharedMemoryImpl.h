#pragma once

#include "utils/SharedMemory.h"

namespace pilecv4j {
namespace ipc {

class SharedMemoryImpl : public SharedMemory {
public:
	inline SharedMemoryImpl(const char* name, int32_t nameRep) : SharedMemory(name, nameRep) {}
	
	inline virtual ~SharedMemoryImpl() { cleanup(); }

	virtual bool createSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep, std::size_t size);
	virtual bool openSharedMemorySegment(SharedMemoryDescriptor* fd, const char* name, int32_t nameRep);
	virtual bool closeSharedMemorySegment(SharedMemoryDescriptor fd, const char* name, int32_t nameRep);
	virtual bool mmapSharedMemorySegment(void** addr, SharedMemoryDescriptor fd, std::size_t size);
	virtual bool unmmapSharedMemorySegment(void* addr, std::size_t size);
};

}
}
