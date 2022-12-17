#include "common/EndTime.h"
#include "common/kog_exports.h"

#include <thread>

#include <sys/mman.h>
#include <fcntl.h>
#include <semaphore.h>
#include <unistd.h>

#include "utils/cvtypes.h"

#include <utils/ShmQueue.h>

#define COMPONENT "MQUE"
#define PCV4K_IPC_TRACE RAW_PCV4J_IPC_TRACE(COMPONENT)

using namespace std::chrono_literals;
using namespace ai::kognition::pilecv4j;

namespace pilecv4j {
namespace ipc {

enum Mode {
  READ, WRITE
};

#define SHM_HEADER_MAGIC 0xBADFADE0CAFEF00D
struct Header {
  uint64_t magic = SHM_HEADER_MAGIC;
  sem_t write_sem;
  sem_t read_sem;
  std::size_t totalSize = 0;
  std::size_t numBytes = 0;
  std::size_t offset = 0;
};

bool ShmQueue::create(std::size_t numBytes, bool powner) {
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
  if (sem_init(&(hptr->write_sem), 1, 1) == -1) {
    errMsg = "Failed to init write semaphore";
    goto error;
  }
  if (sem_init(&(hptr->read_sem), 1, 0) == -1) {
    errMsg = "Failed to init read semaphore";
    goto error;
  }

  // set the sizes
  hptr->totalSize = totalSize;
  hptr->numBytes = numBytes;
  hptr->offset = dataOffset;

  data = ((uint8_t*)addr) + dataOffset;
  if (isEnabled(DEBUG))
    log(DEBUG, COMPONENT, "Allocated shared mem at 0x%lx with offset to data of %d bytes putting the data at 0x%lx", (long)addr, (int)dataOffset, (long)data);

  // set the magic number
  hptr->magic = SHM_HEADER_MAGIC;
  this->isOpen = true;
  return true;

  error:
  {
    int err = errno;
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(ERROR, COMPONENT, "%s. Error %d: %s", errMsg.c_str(), err, msg);
  }
  return false;
}

bool ShmQueue::open(bool powner) {
  Header lheader;
  Header* header;
  EndTime<> endTime;
  int err;

  fd = shm_open(name.c_str(), O_RDWR, S_IRUSR | S_IWUSR);
  if (fd == -1) {
    err = errno;
    if (err == ENOENT) // no such file is a normal condition
      return false;
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
  endTime.set(500ms);
  while(header->magic != SHM_HEADER_MAGIC && !endTime.isTimePast())
    std::this_thread::yield();

  if (header->magic != SHM_HEADER_MAGIC) {
    if (isEnabled(DEBUG))
      log(DEBUG, COMPONENT, "Timed out waiting for the serving size to setup the semaphore");
    return false;
  }

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

  return true;
  error:
  {
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(WARN, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
  }
  return false;
}

void* ShmQueue::tryGetReadView() {
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get a cv::Mat unless the MapQueue is opened first");
    return nullptr;
  }

  Header* header = (Header*)addr;
  if (sem_trywait(&(header->read_sem)) == -1) {
    // this is okay if errno is EAGAIN
    int err = errno;
    if (err != EAGAIN) {
      char erroStr[256];
      const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
      erroStr[sizeof(erroStr) - 1] = (char)0;
      log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
    }
    return nullptr;
  }

  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Returning data for reading at 0x%lx of %ld bytes", (long)data, (long)header->numBytes);
  return data;
}

bool ShmQueue::markRead() {
  Header* header = (Header*)addr;

  // post the sem, making it available for writing again since I've read it.
  if (sem_post(&(header->write_sem)) == -1) {
    int err = errno;
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
    return false;
  }

  return true;
}

std::size_t ShmQueue::bufferSize() {
  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot get buffer size until the shm segment is open");
    return 0;
  }

  Header* header = (Header*)addr;
  return (std::size_t)header->numBytes;
}

void* ShmQueue::tryGetWriteView() {

  if (! isOpen) {
    log(ERROR, COMPONENT, "Cannot send a cv::Mat unless the MapQueue is opened first");
    return nullptr;
  }

  Header* header = (Header*)addr;
  if (sem_trywait(&(header->write_sem)) == -1) {
    // this is okay if errno is EAGAIN
    int err = errno;
    if (err != EAGAIN) {
      char erroStr[256];
      const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
      erroStr[sizeof(erroStr) - 1] = (char)0;
      log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
    }
    return nullptr;
  }

  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Returning data for writing at 0x%lx of %ld bytes", (long)data, (long)header->numBytes);
  return data;
}

bool ShmQueue::markWritten() {
  Header* header = (Header*)addr;

  if (sem_post(&(header->read_sem)) == -1) {
    int err = errno;
    char erroStr[256];
    const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
    erroStr[sizeof(erroStr) - 1] = (char)0;
    log(ERROR, COMPONENT, "Failed to open shared memory segment. Error %d: %s", err, msg);
    return false;
  }

  return true;
}

ShmQueue::~ShmQueue() {
  if (addr && totalSize >= 0)
    if (munmap(addr, totalSize) == -1) {
      int err = errno;
      char erroStr[256];
      const char* msg = strerror_r(err, erroStr, sizeof(erroStr));
      erroStr[sizeof(erroStr) - 1] = (char)0;
      log(ERROR, COMPONENT, "Failed to un-mmap the shared memory segment. Error %d: %s", err, msg);
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

extern "C" {

KAI_EXPORT uint64_t pilecv4j_ipc_create_shmQueue(const char* name) {
  PCV4K_IPC_TRACE;
  return (uint64_t)new ShmQueue(name);
}

KAI_EXPORT void pilecv4j_ipc_destroy_shmQueue(uint64_t nativeRef) {
  PCV4K_IPC_TRACE;
  if (nativeRef)
    delete (ShmQueue*)nativeRef;
}

KAI_EXPORT int32_t pilecv4j_ipc_shmQueue_create(uint64_t nativeRef, uint64_t size, int32_t owner) {
  PCV4K_IPC_TRACE;
  return ((ShmQueue*)nativeRef)->create(size, owner == 0 ? false : true) ? 1 : 0;
}

KAI_EXPORT int32_t pilecv4j_ipc_shmQueue_open(uint64_t nativeRef, int32_t owner) {
  PCV4K_IPC_TRACE;
  return ((ShmQueue*)nativeRef)->open(owner == 0 ? false : true) ? 1 : 0;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_tryGetWriteView_asMat(uint64_t nativeRef, uint64_t numElements, int32_t cvtype) {
  PCV4K_IPC_TRACE;
  void* data = ((ShmQueue*)nativeRef)->tryGetWriteView();
  if (!data)
    return 0;

  cv::Mat* ret = new cv::Mat(1,(int)numElements, (int)cvtype, data);
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Returning mat for writing at 0x%lx of %ld elements, with %d bytes per element. Data at 0x%lx",
        (long)ret, (long)ret->total(), (int)ret->elemSize(), (long)ret->data);

  return (uint64_t)ret;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_tryGetReadView_asMat(uint64_t nativeRef, uint64_t numElements, int32_t cvtype) {
  PCV4K_IPC_TRACE;
  void* data = ((ShmQueue*)nativeRef)->tryGetReadView();
  if (!data)
    return 0;

  cv::Mat* ret = new cv::Mat(1, numElements, cvtype, data);
  if (isEnabled(TRACE))
    log(TRACE, COMPONENT, "Returning mat for writing at 0x%lx of %ld elements, with %d bytes per element. Data at 0x%lx",
        (long)ret, (long)ret->total(), (int)ret->elemSize(), (long)ret->data);
  return (uint64_t)ret;
}

KAI_EXPORT int32_t pilecv4j_ipc_shmQueue_markWritten(uint64_t nativeRef) {
  return ((ShmQueue*)nativeRef)->markWritten() ? 1 : 0;
}

KAI_EXPORT uint64_t pilecv4j_ipc_shmQueue_bufferSize(uint64_t nativeRef) {
  return (uint64_t)((ShmQueue*)nativeRef)->bufferSize();
}

KAI_EXPORT int32_t pilecv4j_ipc_shmQueue_markRead(uint64_t nativeRef) {
  return ((ShmQueue*)nativeRef)->markRead() ? 1 : 0;
}

}
}
}

