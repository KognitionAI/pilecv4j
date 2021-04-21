//#include <dlfcn.h>
//#include <malloc.h>
//
//extern void *__libc_malloc(size_t size);
//
//void *malloc(size_t size)
//{
//  void *ptr = __libc_malloc(size);
//  return ptr;
//}
//
//#include <stdint.h>
//
//static void* (*__real_malloc)(size_t) = NULL;
////static void* (*__real_calloc)(size_t,size_t) = nullptr;
////static void* (*__real_realloc)(void*, size_t);
////static void* (*__real_valloc)(size_t);
////static void* (*__real_memalign)(size_t,size_t);
////static int (*__real_posix_memalign)(void**,size_t,size_t);
////static void (*__real_free)(void*);
////static void* (*__real_pvalloc)(size_t);
////static void* (*__real_aligned_alloc)(size_t, size_t);
//static uint8_t isInited = 0;
//
//static void init() {
//  if (!isInited) {
//    printf("In init\n"); fflush(stdout);
//    __real_malloc = (void* (*)(size_t))dlsym(RTLD_NEXT, "malloc");
//    if (NULL == __real_malloc) {
//      fprintf(stderr, "Error in `dlsym`: %s\n", dlerror());
//    }
//    //    __real_calloc = (void* (*)(size_t, size_t))dlsym(RTLD_NEXT, "calloc");
//    //    __real_realloc = (void* (*)(void*,size_t))dlsym(RTLD_NEXT, "realloc");
//    //    __real_valloc = (void* (*)(size_t))dlsym(RTLD_NEXT, "valloc");
//    //    __real_memalign = (void* (*)(size_t,size_t))dlsym(RTLD_NEXT, "memalign");
//    //    __real_posix_memalign = (int (*)(void**,size_t,size_t))dlsym(RTLD_NEXT, "posix_memalign");
//    //    __real_free = (void (*)(void*))dlsym(RTLD_NEXT, "free");
//    //    __real_pvalloc = (void* (*)(size_t))dlsym(RTLD_NEXT, "pvalloc");
//    //    __real_aligned_alloc = (void* (*)(size_t, size_t))dlsym(RTLD_NEXT, "aligned_alloc");
//    isInited = 1;
//  }
//}
//
//////void* operator new (size_t size)
////extern "C" void* _Znwm(size_t size)
////{
//// void *p=malloc(size);
//// if (p==0) // did malloc succeed?
////  throw std::bad_alloc(); // ANSI/ISO compliant behavior
//// return p;
////}
////
//////void operator delete(void* del)
////extern "C" void _ZdlPv(void* del)
////{
////  if (del != NULL)
////    free(del);
////}
////
////
//////void* operator new[] (size_t size)
////extern "C" void* _Znam(size_t size)
////{
//// void *p=__real_malloc(size);
//// if (p==0) // did malloc succeed?
////  throw std::bad_alloc(); // ANSI/ISO compliant behavior
//// return p;
////}
////
////// void operator delete[] (void*)
////extern "C" void _ZdaPv(void* del)
////{
////  if (del != NULL)
////    __real_free(del);
////}
//
//void* malloc(size_t c)
//{
//  init();
//  void* ret;
//  //__real_posix_memalign(&ret,sizeof(void*),c);
//  ret = __real_malloc(c);
//  return ret;
//}
//
////extern "C" void free(void* f)
////{
////  init();
////  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
////  registerFree(f);
////  __real_free(f);
////}
////
////extern "C" void* calloc(size_t num, size_t size )
////{
////  init();
////  printf("My calloc called with %ld, %ld\n", (long)num, (long)size);
////  return __real_calloc(num, size);
////}
////
////extern "C" void* valloc(size_t size )
////{
////  init();
////  printf("My valloc called with %ld\n", (long)size);
////  return valloc(size);
////}
////
////extern "C" void* memalign(size_t boundary, size_t size )
////{
////  init();
////  printf("My memalign called with %ld, %ld\n", (long)boundary, (long)size);
////  return __real_memalign(boundary, size);
////}
////
////extern "C" int posix_memalign(void** res, size_t boundary, size_t size )
////{
////  init();
////  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
////  int status = __real_posix_memalign(res,boundary, size);
////  registerAlloc(*res,size);
////  return status;
////}
////
////extern "C" void* realloc( void *memblock, size_t size )
////{
////  init();
////  std::lock_guard<std::recursive_mutex> lock(LA->ccrit);
////
////  // You can pass NULL to realloc and it's just a malloc
////  if (memblock == NULL) return malloc(size);
////
////  void* ret = __real_realloc(memblock, size);
////
////  // if we changed addresses then we need to account for the new allocation
////  // and clean up the old one.
////  if (ret != memblock) {
////    registerFree(memblock);
////    registerAlloc(ret,size);
////  } else {  // otherwise we need to mark the existing alloc
////    if (pilecv4j::LeakDetectOff::isLeakDetectEnabled() && LA->leakDetect) {
////      pilecv4j::LeakDetectOff ldo;
////
////      Allocaction a(memblock);
////      std::set<Allocaction>::iterator i = LA->allocations.find(a);
////      if (i != LA->allocations.end()) {
////        a = (*i);
////        a.numBytes = size;
////        LA->allocations.erase(i);
////        LA->allocations.insert(a);
////      } else {
////        printf("Unknown realloc called with 0x%lx, %ld\n", (long)memblock,(long)size);
////        registerAlloc(ret,size);
////      }
////    }
////  }
////
////  return ret;
////}
////
////extern "C" void* aligned_alloc(size_t alignment, size_t size ) {
////  init();
////  printf("My aligned_alloc called with %ld, %ld\n", (long)alignment, (long)size);
////  return __real_aligned_alloc(alignment, size);
////}
////
////extern "C" void* pvalloc(size_t size )
////{
////  init();
////  printf("My pvalloc called with %ld\n", (long)size);
////  return __real_pvalloc(size);
////}
