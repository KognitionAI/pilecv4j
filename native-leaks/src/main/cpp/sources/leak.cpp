//// Should be linked as follows:
////g++ ... -o xbmc.bin -Wl,--wrap,malloc -Wl,--wrap,free -DGLIBCXX_FORCE_NEW -Wl,--wrap,calloc -Wl,--wrap,realloc -Wl,--wrap,valloc -Wl,--wrap,memalign -Wl,--wrap,posix_memalign -Wl,--wrap,_ZdaPv -Wl,--wrap,_ZdlPv -Wl,--wrap,_Znam -Wl,--wrap,_Znwm -Wl,--wrap,aligned_alloc -Wl,--wrap,pvalloc xbmc/main/main.a ...
//// The pertinent section starting wth -Wl, and ending with the last -Wl.
//// To get the link line run make:
////    V=1 make
//// Take the link line, edit it as shown. Then leak detection is in.
//
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

#define LIBNAME "libai.kognition.pilecv4j.leaks.so"

//// 64-bit magic number XBMCCBMX
////#define MAGIC_NUMBER 0x58626D63636D6258
////#define CLEAR_MAGIC_NUMBER (~MAGIC_NUMBER)

#define BTFRAMES 64000

static void* (*__real_malloc)(size_t) = NULL;
////static void* (*__real_calloc)(size_t,size_t) = nullptr;
////static void* (*__real_realloc)(void*, size_t);
////static void* (*__real_valloc)(size_t);
////static void* (*__real_memalign)(size_t,size_t);
static int (*__real_posix_memalign)(void**,size_t,size_t);
static void (*__real_free)(void*);
////static void* (*__real_pvalloc)(size_t);
////static void* (*__real_aligned_alloc)(size_t, size_t);

template<typename Func> void safevoidcall(Func f) {
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled()) {
    pilecv4j::LeakDetectOff ldo;
    f();
  }
}

static uint8_t isInited = 0;

static void init() {
  if (!isInited) {
    safevoidcall([](){fprintf(stdout, "In init\n"); fflush(stdout);});

    __real_malloc = (void* (*)(size_t))dlsym(RTLD_NEXT, "malloc");
    if (!__real_malloc)
      safevoidcall([](){fprintf(stderr, "ERROR: Failed to retrieve actual malloc\n"); fflush(stderr);});
    //    __real_calloc = (void* (*)(size_t, size_t))dlsym(RTLD_NEXT, "calloc");
    //    __real_realloc = (void* (*)(void*,size_t))dlsym(RTLD_NEXT, "realloc");
    //    __real_valloc = (void* (*)(size_t))dlsym(RTLD_NEXT, "valloc");
    //    __real_memalign = (void* (*)(size_t,size_t))dlsym(RTLD_NEXT, "memalign");
    __real_posix_memalign = (int (*)(void**,size_t,size_t))dlsym(RTLD_NEXT, "posix_memalign");
    if (!__real_posix_memalign)
      safevoidcall([](){fprintf(stderr, "ERROR: Failed to retrieve actual posix_memalign\n"); fflush(stderr);});
    __real_free = (void (*)(void*))dlsym(RTLD_NEXT, "free");
    if (!__real_free)
      safevoidcall([](){fprintf(stderr, "ERROR: Failed to retrieve actual posix_memalign\n"); fflush(stderr);});
    //    __real_pvalloc = (void* (*)(size_t))dlsym(RTLD_NEXT, "pvalloc");
    //    __real_aligned_alloc = (void* (*)(size_t, size_t))dlsym(RTLD_NEXT, "aligned_alloc");
    isInited = 1;
  }
}



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
  inline ~Backtrace() {}
  inline Backtrace(const Backtrace& o) : bt(o.bt), frames(o.frames) {}
  inline Backtrace& operator=(const Backtrace& o) { bt=o.bt; frames = o.frames; return *this; }

  inline bool isSet() const { return bt != NULL; }

  inline void set(void* pbt[], size_t pframes) {
    init();
    frames = pframes;
    size_t numbytes = pframes * sizeof(void*);
    bt = (void**)__real_malloc(numbytes);
    for (size_t i = 0; i < pframes; i++)
      bt[i] = pbt[i];
  }

  inline void free() { init(); if (bt != NULL) __real_free(bt); }
  void dump() const;
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
  inline Allocaction& operator=(const Allocaction& o) { mbt = o.mbt; fbt = o.fbt; numBytes = o.numBytes; alloc = o.alloc; seq = o.seq; return *this; }

  inline void free() { mbt.free(); fbt.free(); }

  void dump() const;
};

class LeakAdmin
{
public:
  volatile bool leakDetect;
  std::set<Allocaction> allocations;
  unsigned long curSequence;
  unsigned long mark;
  std::recursive_mutex ccrit;
  void* sBacktrace[BTFRAMES];
  std::vector<std::tuple<unsigned long,std::string> > marks;

  inline LeakAdmin() : leakDetect(true), curSequence(0), mark((unsigned long)-1) {
    safevoidcall([](){printf("Initializing Leak Detection.\n");});
    memset(sBacktrace,0,(BTFRAMES * sizeof(void*)));
    atexit(wrapup);
  }

  inline void* operator new(size_t size) { init(); return __real_malloc(size);  }
};

static inline LeakAdmin* getLeakAdmin()
{
  static LeakAdmin* instance = NULL;
  if (instance == NULL)
    instance = new LeakAdmin();
  return instance;
}

// The BSS segment will set this to true on initialization
struct InitSetter { inline InitSetter() { getLeakAdmin(); } };
static InitSetter isetter;

#define LA getLeakAdmin()

static inline bool checkForFrame(char** bt, int numFrames,int framenum, const char* check) {
  return (numFrames > framenum) && (strstr(((char*)(bt[framenum])),check) != NULL);
}

static inline bool checkCalledFrom(char** bt, int numFrames,const char* check) {
  pilecv4j::LeakDetectOff ldo;
  int i;
  for (i = 0; i < numFrames; i++) {
    if (strstr(bt[i], LIBNAME) == NULL)
      break;
  }

  if (i < numFrames) {
    bool ret = strstr(bt[i], check) != NULL;
    fprintf(stdout, "checkCalledFrom called from: %s", bt[i]);
    return ret;
  }
  else
    return false;
}

// Must only be called while LD is OFF
void Allocaction::dump() const {
    printf("%ld'th, %ld bytes allocated at 0x%lx\n",seq,(long)numBytes,(long)alloc);
    printf("Alloced at:\n");
    mbt.dump();
    printf("Freed at:\n");
    fbt.dump();
}

// Must only be called while LD is OFF
void Backtrace::dump() const {
    if (!isSet()) {
      printf("Unset backtrace\n");
      return;
    }

    char** sbt = backtrace_symbols(bt,frames);
    // filter out some things that are either not leaks, or I have no control of anyway
    for (size_t i = 0; i < frames; i++)
      printf("    %s\n",sbt[i]);
    printf("\n");
    __real_free(sbt);
}

#define BACKTRACE(bt) { size_t bt__frames = backtrace(LA->sBacktrace, BTFRAMES); (bt).set(LA->sBacktrace,bt__frames); }

static void registerAlloc(void* alloc, size_t c) {
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled() && LA->leakDetect) {
    pilecv4j::LeakDetectOff ldo;
    //safevoidcall([&res,&boundary,&size]() { printf("My posix_memalign called with 0x%lx, %ld, %ld\n", (long)res,(long)boundary, (long)size)})

    Allocaction a(alloc,c,LA->curSequence++);
    BACKTRACE(a.mbt); // fill in backtrace
//    char** sbt = backtrace_symbols(a.mbt.bt, a.mbt.frames);
//    checkCalledFrom(sbt, a.mbt.frames, "libjvm.so");
//    __real_free(sbt);

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
}

static unsigned char pattern[] = { 0xde, 0xad, 0xbe, 0xef };
static size_t mask = 0x3;

static void registerFree(void* f) {
  if (pilecv4j::LeakDetectOff::isLeakDetectEnabled() && LA->leakDetect) {
    pilecv4j::LeakDetectOff ldo;
//    printf("My free called with 0x%lx\n", (long)f);

    Allocaction a(f);
    std::set<Allocaction>::iterator i = LA->allocations.find(a);
    if (i != LA->allocations.end()) {
      a = (*i);
      for (size_t index = 0; index < a.numBytes; index++) {
        ((unsigned char*)f)[index] = pattern[index & mask];
      }
      Backtrace curFree;
      BACKTRACE(curFree);
      if (a.fbt.isSet()) {
        // double free!!!
        printf("Double Free!!!!\n");
        a.dump();
        printf("Second free pos:\n");
        curFree.dump();
        curFree.free();
      } else {
        a.fbt = curFree;
      }
      LA->allocations.erase(i);
      LA->allocations.insert(a);
    } else if (f != NULL) {
      printf("free called on unknown alloc 0x%lx\n", (long)f);
    }
  }
}



namespace pilecv4j
{
//  void StartLeakTracking()
//  {
//    printf("Starting leak detection.\n");
//    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
//    LA->leakDetect = true;
//  }

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
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      for (std::set<Allocaction>::iterator iter = LA->allocations.begin();
           iter != LA->allocations.end(); iter++)
      {
        Allocaction a(*iter);
        a.free();
      }
      LA->allocations.clear();
    }
  }

  void EndLeakTracking()
  {
    printf("Ending leak detection.\n");
    {
      std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
      LA->leakDetect = false;
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
//
//  bool IsLeakTrackingOn()
//  {
//    std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
//    return LA->leakDetect;
//  }

  static thread_local pilecv4j::LeakDetectOff* leakDetectBlock;

  bool LeakDetectOff::isLeakDetectEnabled() { return leakDetectBlock == NULL; }

  LeakDetectOff::LeakDetectOff() : recursed(false)
  {
    if (leakDetectBlock == NULL)
      leakDetectBlock = this;
    else
      recursed = true;
  }

  LeakDetectOff::~LeakDetectOff()
  {
    if (!recursed && leakDetectBlock == this)
      leakDetectBlock = nullptr;
  }
}

extern "C" int posix_memalign(void** res, size_t boundary, size_t size )
{
  init();
  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
  int status = __real_posix_memalign(res,boundary, size);
  registerAlloc(*res,size);
  return status;
}

extern "C" void *malloc(size_t size)
{
  init();
  void* ret;
  if (posix_memalign(&ret,sizeof(void*),size) != 0) {
    safevoidcall([&size, &ret]() {fprintf(stdout, "Malloc(%zd)->(%p), underlying=%ld\n", size, ret, (uint64_t)__real_malloc); fflush(stdout);});
  }
  safevoidcall([&size, &ret]() {fprintf(stdout, "Malloc(%zd)->(%p), underlying=%ld\n", size, ret, (uint64_t)__real_malloc); fflush(stdout);});
  return ret;
}

extern "C" void free(void* f)
{
  init();
//  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
//  registerFree(f);
  __real_free(f);
}

