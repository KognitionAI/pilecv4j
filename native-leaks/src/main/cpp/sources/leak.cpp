#include "leak.h"
#include <stdint.h>
#include <stdlib.h>
#include <malloc.h>
#include <stdbool.h>
#include <threads.h>
#include <stdio.h>
#include <dlfcn.h>

#include <string.h>
#include <execinfo.h>
#include <set>
#include <vector>
#include <string>
#include <thread>
#include <mutex>

#include <signal.h>

#define LIBNAME "libai.kognition.pilecv4j.leaks.so"

#define BTFRAMES 64000

template<typename Func> void safevoidcall(Func f) {
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
    pilecv4j::LeakDetectOff ldo;
    f();
  }
}

static bool trackAllocations();


// This is called from atexit, just in case we forgot to close
static void wrapup()
{
  safevoidcall([](){printf ("Final leak detection wrapup.\n");});
  pilecv4j::EndLeakTracking();
}

struct Backtrace {
  void** bt;
  size_t frames;

  inline Backtrace() : bt(NULL), frames(0) {}
  inline ~Backtrace() { free(); }
  inline Backtrace(const Backtrace& o) : bt(nullptr), frames(o.frames) {
    if (o.isSet())
      set(o.bt, o.frames);
  }
  Backtrace& operator=(const Backtrace& o) = delete;

  inline bool isSet() const { return bt != NULL; }

  inline void set(void* pbt[], size_t pframes) {
    pilecv4j::LeakDetectOff ldo;
    frames = pframes;
    size_t numbytes = pframes * sizeof(void*);
    bt = (void**)malloc(numbytes);
    for (size_t i = 0; i < pframes; i++)
      bt[i] = pbt[i];
  }

  void dump() const;

  void dump(char** symbols) const;

private:
  inline void free() {
    if (bt) {
      pilecv4j::LeakDetectOff ldo;
      ::free(bt);
    }
  }
};

struct Allocaction
{
  Backtrace mbt;
  Backtrace fbt;
  size_t numBytes;
  void* alloc;
  unsigned long seq;

  inline Allocaction(void* palloc, size_t pnumBytes, unsigned long sequence) : numBytes(pnumBytes), alloc(palloc), seq(sequence) { }
  inline Allocaction(void* palloc) : numBytes(0), alloc(palloc), seq(0) {}
  inline Allocaction(const Allocaction& o) : mbt(o.mbt), fbt(o.fbt), numBytes(o.numBytes), alloc(o.alloc), seq(o.seq) {}

  inline bool operator<(const Allocaction& o) const { return alloc < o.alloc; }
  inline bool operator==(const Allocaction& o) const { return alloc == o.alloc; }
  Allocaction& operator=(const Allocaction& o) = delete;

  void dump() const;
};

class LeakAdmin
{
public:
  std::set<Allocaction> allocations;
  unsigned long curSequence;
  unsigned long mark;
  std::recursive_mutex ccrit;
  void** sBacktrace;
  std::vector<std::tuple<unsigned long,std::string> > marks;
  sighandler_t prev = nullptr;

  inline LeakAdmin() : curSequence(0), mark((unsigned long)-1) {
    pilecv4j::LeakDetectOff ldo;
    printf("Initializing Leak Detection.\n");
    sBacktrace = (void**) malloc(sizeof(void*)*BTFRAMES);
    memset(sBacktrace,0,(BTFRAMES * sizeof(void*)));
    atexit(wrapup);
  }

  inline void* operator new(size_t size) {
    pilecv4j::LeakDetectOff ldo;
    return malloc(size);
  }
};

static inline LeakAdmin* getLeakAdmin()
{
  static LeakAdmin* instance = NULL;
  if (instance == NULL)
    instance = new LeakAdmin();
  return instance;
}

static void SigDump(int);

// The BSS segment will set this to true on initialization
struct InitSetter {
  inline InitSetter() {
    getLeakAdmin();
    getLeakAdmin()->prev = signal(SIGUSR1,SigDump);
  }
};
static InitSetter isetter;

#define LA getLeakAdmin()

//static inline bool checkForFrame(char** bt, int numFrames,int framenum, const char* check) {
//  pilecv4j::LeakDetectOff ldo;
//  return (numFrames > framenum) && (strstr(((char*)(bt[framenum])),check) != NULL);
//}

static inline bool checkCalledFrom(char** bt, int numFrames,const char* check) {
  pilecv4j::LeakDetectOff ldo;
  int i;
  for (i = 0; i < numFrames; i++) {
    if (strstr(bt[i], LIBNAME) == NULL)
      break;
  }

  if (i < numFrames) {
    bool ret = strstr(bt[i], check) != NULL;
    return ret;
  }
  else
    return false;
}

// Must only be called while LD is OFF
void Allocaction::dump() const {
  pilecv4j::LeakDetectOff ldo;
  // if the allocation was done from java then skip it.
  if (mbt.isSet()) {
    char** sbt = backtrace_symbols(mbt.bt,mbt.frames);
    if (!checkCalledFrom(sbt, mbt.frames,"libjvm.so")) {
      printf("%ld'th, %ld bytes allocated at 0x%lx\n",seq,(long)numBytes,(long)alloc);
      printf("Alloced at:\n");
      mbt.dump(sbt);
      printf("Freed at:\n");
      fbt.dump();
    }
    ::free(sbt);
  } else if (fbt.isSet()) {
    char** sbt = backtrace_symbols(fbt.bt,fbt.frames);
    if (!checkCalledFrom(sbt, fbt.frames,"libjvm.so")) {
      printf("%ld'th, %ld bytes allocated at 0x%lx\n",seq,(long)numBytes,(long)alloc);
      printf("Alloced at:\n");
      mbt.dump();
      printf("Freed at:\n");
      fbt.dump(sbt);
    }
    ::free(sbt);
  }
}

// Must only be called while LD is OFF
void Backtrace::dump() const {
  pilecv4j::LeakDetectOff ldo;
  if (!isSet()) {
    printf("Unset backtrace\n");
    return;
  }

  char** sbt = backtrace_symbols(bt,frames);
  dump(sbt);
  ::free(sbt);
}

void Backtrace::dump(char** sbt) const {
  // filter out some things that are either not leaks, or I have no control of anyway
  for (size_t i = 0; i < frames; i++)
    printf("    %s\n",sbt[i]);
  printf("\n");
}

#define BACKTRACE(bt) { size_t bt__frames = backtrace(LA->sBacktrace, BTFRAMES); (bt).set(LA->sBacktrace,bt__frames); }

static void registerAlloc(void* alloc, size_t c) {
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled())
    return;

  if (!alloc)
    return;

  Allocaction a(alloc,c,LA->curSequence++);
  BACKTRACE(a.mbt); // fill in backtrace

  // do we already have a free allocation
  std::set<Allocaction>::iterator i = LA->allocations.find(a);
  if (i != LA->allocations.end()) {
    // we have a 'free' ... lets to a simple error check.
    const Allocaction& o = (*i);
    if (!o.fbt.isSet()) {
      printf("ERROR IN TRACKING ... MALLOC RETURNED AN ALREADY ALLOCATED BLOCK:\n");
      o.dump();
      printf("   ALLOCATED AGAIN AT:\n");
      Backtrace bt;
      BACKTRACE(bt);
      bt.dump();
    }
    LA->allocations.erase(i);
  }

  LA->allocations.insert(a);
}

static void registerFree(void* f) {
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled())
    return;

  if (!f)
    return;
  Allocaction aa(f);
  std::set<Allocaction>::iterator i = LA->allocations.find(aa);
  if (i != LA->allocations.end()) {
    Allocaction a(*i);
    if (a.fbt.isSet()) {
      // double free!!!
      printf("Double Free!!!!\n");
      a.dump();
      printf("Second free pos:\n");
      Backtrace curFree;
      BACKTRACE(curFree);
      curFree.dump();
    } else {
      BACKTRACE(a.fbt);
    }
    LA->allocations.erase(i);
    LA->allocations.insert(a);
  } else if (f != NULL) {
    printf("free called on unknown alloc 0x%lx\n", (long)f);
  }
}

namespace pilecv4j
{

  struct SortBySequence
  {
    inline bool operator()(const Allocaction& lhs, const Allocaction& rhs)
    {
      return lhs.seq < rhs.seq;
    }
  };

  static void dumpMark(std::tuple<unsigned long,std::string>& mark)
  {
    printf("===========================================================\n");
    printf("%ld: %s\n", std::get<0>(mark), std::get<1>(mark).c_str());
    printf("===========================================================\n");
    printf("\n");
  }

  void DumpAllocs()
  {
    pilecv4j::LeakDetectOff ldo;
    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
    printf("Dumping leaks detection.\n");
    std::set<Allocaction,SortBySequence> sortedSet;
    for (std::set<Allocaction>::iterator iter = LA->allocations.begin();
         iter != LA->allocations.end(); iter++)
    {
      sortedSet.insert(*iter);
    }

    std::vector<std::tuple<unsigned long,std::string> >::iterator markIter = LA->marks.begin();
    std::tuple<unsigned long,std::string> nextMark = markIter != LA->marks.end() ? (*markIter) : std::tuple<unsigned long,std::string>((unsigned long)-1,"");
    for (std::set<Allocaction>::iterator iter = sortedSet.begin();
         iter != sortedSet.end(); iter++)
    {
      Allocaction a(*iter);
      while (a.seq > std::get<0>(nextMark))
      {
        dumpMark(nextMark);
        markIter++;
        nextMark = markIter != LA->marks.end() ? (*markIter) : std::tuple<unsigned long,std::string>((unsigned long)-1,"");
      }
      if (!a.fbt.isSet()) a.dump();
    }

    while (markIter != LA->marks.end())
      dumpMark(*markIter++);
  }

  void ClearAllocs()
  {
    printf("Clearing leak detection allocs.\n");
    {
      pilecv4j::LeakDetectOff ldo;
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      LA->allocations.clear();
    }
  }

  void EndLeakTracking()
  {
    printf("Ending leak detection.\n");
    {
      pilecv4j::LeakDetectOff ldo;
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      DumpAllocs();
      ClearAllocs();
    }
  }

//  void MarkLeakTracking(const char* trackText)
//  {
//    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
//    LeakDetectOff ldo;
//    LA->marks.push_back(std::tuple<unsigned long,std::string>(LA->curSequence++,std::string(trackText)));
//  }

  static thread_local pilecv4j::LeakDetectOff* leakDetectBlock = nullptr;

  bool LeakDetectOff::isLeakDetectEnabled() { return leakDetectBlock == nullptr; }

  LeakDetectOff::LeakDetectOff() : recursed(false)
  {
    if (leakDetectBlock == nullptr)
      leakDetectBlock = this;
    else
      recursed = true;
  }

  LeakDetectOff::~LeakDetectOff()
  {
    if (!recursed && leakDetectBlock == nullptr)
      leakDetectBlock = nullptr;
  }
}

#define PCV4J_INTERCEPT_INIT(realName, type) \
    { \
      pilecv4j::LeakDetectOff ldo; \
      if (!__real_##realName) { \
        __real_##realName = (type)dlsym(RTLD_NEXT, #realName); \
        if (!__real_##realName) \
          fprintf(stderr, "ERROR: Failed to retrieve actual %s\n", #realName); fflush(stderr); \
      } \
    }

static int (*__real_posix_memalign)(void**,size_t,size_t) = nullptr;
extern "C" int posix_memalign(void** res, size_t boundary, size_t size )
{
  PCV4J_INTERCEPT_INIT(posix_memalign, int (*)(void**,size_t,size_t));
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
    pilecv4j::LeakDetectOff ldo;
    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
    int status = __real_posix_memalign(res,boundary, size);
    registerAlloc(*res,size);
    return status;
  } else {
    return __real_posix_memalign(res,boundary, size);
  }
}

static void* (*__real_malloc)(size_t) = nullptr;
extern "C" void *malloc(size_t size)
{
  PCV4J_INTERCEPT_INIT(malloc, void* (*)(size_t));
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
    pilecv4j::LeakDetectOff ldo;
    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
    void* ret = __real_malloc(size);
    registerAlloc(ret,size);
    return ret;
  } else {
    return __real_malloc(size);
  }
}

static void* (*__real_realloc)(void*, size_t) = nullptr;
extern "C" void* realloc( void *memblock, size_t size )
{
  PCV4J_INTERCEPT_INIT(realloc, void* (*)(void*, size_t));
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
    void* ret = __real_realloc(memblock, size);

//    safevoidcall([&memblock, &size, &ret]() {fprintf(stderr, "realloc(0x%lx, %zd)->(%p)\n", (uint64_t)memblock, size, ret); fflush(stderr);});

    pilecv4j::LeakDetectOff ldo;
    // if we changed addresses then we need to account for the new allocation
    // and clean up the old one.
    if (ret != memblock) {
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      if (memblock)
        registerFree(memblock);
      if (size)
        registerAlloc(ret,size);
    } else {  // otherwise we need to mark the existing alloc
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
        pilecv4j::LeakDetectOff ldo;

        Allocaction aa(memblock);
        std::set<Allocaction>::iterator i = LA->allocations.find(aa);
        if (i != LA->allocations.end()) {
          Allocaction a(*i);
          a.numBytes = size;
          LA->allocations.erase(i);
          LA->allocations.insert(a);
        } else {
          printf("Unknown realloc called with 0x%lx, %ld\n", (long)memblock,(long)size);
          registerAlloc(ret,size);
        }
      }
    }

    return ret;
  } else
    return __real_realloc(memblock, size);
}

// dlsym calls calloc so we need to implement calloc to not use any other
// allocations for at least the one call during the dlsym call, which happens
// in PCV4J_INTERCEPT_INIT. The masterful solution (to respond in the one case
// with a static buffer from the BSS) was found on stackoverflow. See:
// https://stackoverflow.com/questions/7910666/problems-with-ld-preload-and-calloc-interposition-for-certain-executables
static unsigned char buffer[8192];
static void* (*__real_calloc)(size_t,size_t) = nullptr;
static thread_local bool in_calloc = false;
extern "C" void* calloc(size_t num, size_t size )
{
  if (in_calloc) {
    return buffer;
  } else {
    in_calloc = true;
    PCV4J_INTERCEPT_INIT(calloc, void* (*)(size_t,size_t));
    void* ret= __real_calloc(num, size);

    if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      pilecv4j::LeakDetectOff ldo;
      registerAlloc(ret,num*size);
    }

    in_calloc = false;
    return ret;
  }
}

static void* (*__real_valloc)(size_t) = nullptr;
extern "C" void* valloc(size_t size )
{
  PCV4J_INTERCEPT_INIT(valloc, void* (*)(size_t));
  safevoidcall([&size]() {printf("My valloc called with %ld\n", (long)size);});
  return __real_valloc(size);
}

static void* (*__real_memalign)(size_t,size_t) = nullptr;
extern "C" void* memalign(size_t boundary, size_t size )
{
  PCV4J_INTERCEPT_INIT(memalign, void* (*)(size_t,size_t));
  safevoidcall([&size, &boundary]() {printf("My memalign called with %ld, %ld\n", (long)boundary, (long)size);});
  return __real_memalign(boundary, size);
}

static void* (*__real_aligned_alloc)(size_t, size_t) = nullptr;
extern "C" void* aligned_alloc(size_t alignment, size_t size ) {
  PCV4J_INTERCEPT_INIT(aligned_alloc,void* (*)(size_t, size_t));
  safevoidcall([&alignment, &size]() {printf("My aligned_alloc called with %ld, %ld\n", (long)alignment, (long)size);});
  return __real_aligned_alloc(alignment, size);
}

static void* (*__real_pvalloc)(size_t) = nullptr;
extern "C" void* pvalloc(size_t size )
{
  PCV4J_INTERCEPT_INIT(pvalloc,void* (*)(size_t));
  safevoidcall([&size]() {printf("My pvalloc called with %ld\n", (long)size);});
  return __real_pvalloc(size);
}


//void* operator new (size_t size)
extern "C" void* _Znwm(size_t size)
{
 //safevoidcall([&size](){fprintf(stdout, "new %ld\n", size); fflush(stdout);});
 return malloc(size);
}

//void operator delete(void* del)
extern "C" void _ZdlPv(void* del)
{
  //safevoidcall([&del](){fprintf(stdout, "delete 0x%lx\n", (uint64_t)del); fflush(stdout);});
  if (del != NULL)
    free(del);
}


//void* operator new[] (size_t size)
extern "C" void* _Znam(size_t size)
{
  //safevoidcall([&size](){fprintf(stdout, "new[] %ld\n", size); fflush(stdout);});
  return malloc(size);
}

// void operator delete[] (void*)
extern "C" void _ZdaPv(void* del)
{
  //safevoidcall([&del](){fprintf(stdout, "delete[] 0x%lx\n", (uint64_t)del); fflush(stdout);});
  if (del != NULL)
    free(del);
}
static void (*__real_free)(void*) = nullptr;
extern "C" void free(void* f)
{
  if (!f)
    return;
  PCV4J_INTERCEPT_INIT(free, void (*)(void*));
  //safevoidcall([&f](){fprintf(stdout, "free 0x%lx\n", (uint64_t)f); fflush(stdout);});
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
    pilecv4j::LeakDetectOff ldo;
    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
    registerFree(f);
  }
  __real_free(f);
}

// TODO: must make this work recursively.
static bool alreadyRestartedTracking = false;
static thread_local bool trackingAllocationsOn = true;

extern "C" void start_tracking_allocations() {

  // if we're moving to do fine grained tracking with start/stops then we need to
  // restart the tracking from scratch the first time we switch to this mode
  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
  pilecv4j::LeakDetectOff ldo;
  if (!alreadyRestartedTracking) {
    printf("tracking of allocations moving to explicit mode\n");
    pilecv4j::ClearAllocs();
    alreadyRestartedTracking = true;
  }

  printf("ENTERING TRACKING\n");
  trackingAllocationsOn = true;
}

extern "C" void stop_tracking_allocations() {
  if (!trackingAllocationsOn) {
    pilecv4j::LeakDetectOff ldo;
    fprintf(stderr, "ERROR: stop tracking allocations requested but a previous start wasn't requested from this thread.");
    return;
  }

//  // if we're moving to do fine grained tracking with start/stops then we need to
//  // restart the tracking from scratch the first time we switch to this mode
//  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
//  pilecv4j::LeakDetectOff ldo;
//  if (!alreadyRestartedTracking) {
//    printf("tracking of allocations moving to explicit mode\n");
//    pilecv4j::ClearAllocs();
//    alreadyRestartedTracking = true;
//  }
//
//  printf("ENTERING TRACKING\n");
//  trackingAllocationsOn = true;
}

static void SigDump(int signum) {
  if (LA->prev)
    (*(LA->prev))(signum);
  pilecv4j::DumpAllocs();
}

