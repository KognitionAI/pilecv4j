//// g++ -O2 -Wall -Wextra -shared -fPIC -o replace.so replace.cpp -I /opt/jdk/include/ -I /opt/jdk/include/linux/ -std=c++11 -L /opt/jdk/lib/server/ -ljvm -Wl,-rpath,/opt/jdk/lib/server/
//// LD_PRELOAD=./replace.so java -classpath .:../battlecode/java Player
//
//#include <unistd.h>
//#include <string.h>
//#include <stdlib.h>
//#include <stdio.h>
//#include <dlfcn.h>
//#include <link.h>
//#include <atomic>
//#include <jni.h>
//
//static void* (*real_malloc)(size_t) = NULL;
//static void (*real_free)(void*) = NULL;
//static void* (*real_realloc)(void*, size_t) = NULL;
//static void* (*real_calloc)(size_t, size_t) = NULL;
//static void* (*real_memalign)(size_t, size_t) = NULL;
//static int (*real_posix_memalign)(void**, size_t, size_t) = NULL;
//static size_t (*real_malloc_usable_size)(void*) = NULL;
//static char* (*real_strdup)(const char*) = NULL;
//static char* (*real_strndup)(const char*, size_t) = NULL;
//
//static char tmpbuff[1024];
//static size_t tmppos = 0;
//
//static std::atomic<uintptr_t> rust_lib_start, rust_lib_len;
//
//static bool initializing = false;
//static void malloc_init(void) {
//  initializing = 1;
//#define LOAD(name) \
//    real_##name = (decltype(real_##name))dlsym(RTLD_NEXT, #name); \
//    if (!real_##name) fprintf(stderr, "Error in `dlsym`: %s, proceeding anyway\n", dlerror())
//  LOAD(malloc);
//  LOAD(free);
//  LOAD(realloc);
//  LOAD(calloc);
//  LOAD(memalign);
//  LOAD(posix_memalign);
//  LOAD(malloc_usable_size);
//  LOAD(strdup);
//  LOAD(strndup);
//  initializing = 0;
//}
//
//inline static void ensure_malloc() {
//  if (!real_malloc)
//    malloc_init();
//}
//
//static void __attribute__((constructor)) init() {
//  // try to call malloc ASAP before things become multi-threaded...
//  ensure_malloc();
//}
//
//static int find_rust_callback(struct dl_phdr_info *info, size_t, void *data) {
//  if (strstr(info->dlpi_name, "battlecode")) {
//    uintptr_t min = (uintptr_t)(-1);
//    uintptr_t max = 0;
//    for (int j = 0; j < info->dlpi_phnum; j++) {
//      uintptr_t start = info->dlpi_phdr[j].p_vaddr;
//      uintptr_t end = info->dlpi_phdr[j].p_vaddr + info->dlpi_phdr[j].p_memsz;
//      if (start < min) min = start;
//      if (end > max) max = end;
//    }
//    if (max < min)
//      max = min = 0;
//    rust_lib_len.store(max - min);
//    rust_lib_start.store(info->dlpi_addr + min);
//    *(bool*)data = true;
//  }
//  return 0;
//}
//
//static bool is_rust_caller(void* addr) {
//  uintptr_t start = rust_lib_start.load(std::memory_order_acquire);
//  if (!start) {
//    bool found = false;
//    dl_iterate_phdr(find_rust_callback, &found);
//    if (!found) return false; // not loaded yet
//    start = rust_lib_start.load();
//  }
//  uintptr_t len = rust_lib_len.load(std::memory_order_relaxed);
//  if ((uintptr_t)addr - start < len) {
//    return true;
//  }
//  return false;
//}
//
//struct Jalloc {
//  jbyteArray jba;
//  jobject ref;
//  size_t size;
//  size_t padding; // for good measure
//};
//
//static std::atomic<JavaVM*> cached_jvm;
//
//[[noreturn]]
// static void fail(const char* str) {
//  (void)write(1, str, strlen(str));
//  (void)write(1, "\n", 1);
//  // Crash. abort(); would be nicer, but that breaks stack traces with musl.
//  *(volatile int*)0 = 0;
//  for (;;);
//}
//
//static JNIEnv* JNU_GetEnv() {
//  JNIEnv *env;
//  jint rc = cached_jvm.load(std::memory_order_relaxed)->GetEnv((void **)&env, JNI_VERSION_1_2);
//  if (rc == JNI_EDETACHED) fail("JNI_EDETACHED");
//  if (rc == JNI_EVERSION) fail("JNI_EVERSION");
//  return env;
//}
//
//static void* my_malloc(size_t size) {
//  Jalloc *pJalloc;
//  if (!cached_jvm.load(std::memory_order_relaxed)) {
//    jsize count = 0;
//    JavaVM* vm;
//    jint status = JNI_GetCreatedJavaVMs(&vm, 1, &count);
//    if (status == JNI_OK && count == 1) {
//      cached_jvm.store(vm);
//      printf("malloc hook initialized!\n");
//    } else {
//      fail("unable to load jvm...");
//    }
//  }
//
//  JNIEnv *env = JNU_GetEnv();
//  jbyteArray jba = env->NewByteArray((int)size + sizeof(Jalloc));
//  if (env->ExceptionOccurred()) fail("exception 1");
//  pJalloc = (Jalloc*)(void*)env->GetByteArrayElements(jba, nullptr);
//  if (env->ExceptionOccurred()) fail("exception 2");
//  pJalloc->jba = jba;
//  pJalloc->ref = env->NewGlobalRef(jba);
//  if (env->ExceptionOccurred()) fail("exception 3");
//  pJalloc->size = size;
//  return (void*)((char*)pJalloc + sizeof(Jalloc));
//}
//
//static void my_free(void* ptr) {
//  Jalloc* pJalloc = (Jalloc*)((char*)ptr - sizeof(Jalloc));
//  JNIEnv *env = JNU_GetEnv();
//  env->DeleteGlobalRef(pJalloc->ref);
//  env->ReleaseByteArrayElements(pJalloc->jba, (jbyte*)(void*)pJalloc, JNI_ABORT);
//}
//
//extern "C" {
//void* malloc(size_t size) {
//  if (size == 0) return nullptr;
//  if (!real_malloc) {
//    // fetch the malloc function. this may call malloc, so...
//    // (from https://stackoverflow.com/questions/6083337/)
//    if (!initializing) {
//      malloc_init();
//    } else {
//      if (tmppos + size < sizeof(tmpbuff)) {
//        void* retptr = tmpbuff + tmppos;
//        tmppos += size;
//        return retptr;
//      } else {
//        fail("too much memory requested during initialisation - increase tmpbuff size");
//      }
//    }
//  }
//
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_malloc(size);
//  else
//    return my_malloc(size);
//}
//
//void free(void* ptr) {
//  if (!ptr) return;
//  if ((uintptr_t)ptr - (uintptr_t)tmpbuff <= tmppos) {
//    // freeing temp memory, just ignore
//    return;
//  }
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    real_free(ptr);
//  else
//    my_free(ptr);
//}
//
//void* realloc(void* ptr, size_t size) {
//  if ((uintptr_t)ptr - (uintptr_t)tmpbuff <= tmppos)
//    fail("shouldn't realloc temp memory");
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_realloc(ptr, size);
//
//  if (!ptr) return malloc(size);
//  else if (size == 0) { free(ptr); return nullptr; }
//  else {
//    size_t orig_size = ((Jalloc*)((char*)ptr - sizeof(Jalloc)))->size;
//    if (size <= orig_size)
//      return ptr;
//    void* mem = my_malloc(size);
//    memcpy(mem, ptr, orig_size);
//    my_free(ptr);
//    return mem;
//  }
//}
//
//void* calloc(size_t a, size_t b) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_calloc(a, b);
//  if (a == 0 || b == 0) return nullptr;
//  void* mem = my_malloc(a*b);
//  memset(mem, 0, a*b);
//  return mem;
//}
//
//void* memalign(size_t a, size_t b) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_memalign(a, b);
//  fail("*** memalign, unable to handle!");
//}
//
//void* aligned_alloc(size_t a, size_t b) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_memalign(a, b); // memalign, aligned_alloc, what's the difference
//  fail("*** aligned_alloc, unable to handle!");
//}
//
//int posix_memalign(void** res, size_t align, size_t len) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_posix_memalign(res, align, len);
//  fail("*** posix_memalign, unable to handle!");
//}
//
//char* strdup(const char* s) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_strdup(s);
//  size_t len = strlen(s);
//  char* ret = (char*)my_malloc(len + 1);
//  memcpy(ret, s, len + 1);
//  return ret;
//}
//
//char* strndup(const char* s, size_t n) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_strndup(s, n);
//  size_t len = strlen(s);
//  if (n < len) len = n;
//  char* ret = (char*)my_malloc(len + 1);
//  memcpy(ret, s, len + 1);
//  ret[len] = '\0';
//  return ret;
//}
//
//size_t malloc_usable_size(void* ptr) {
//  ensure_malloc();
//  if (!is_rust_caller(__builtin_return_address(0)))
//    return real_malloc_usable_size(ptr);
//  Jalloc* pJalloc = (Jalloc*)((char*)ptr - sizeof(Jalloc));
//  return pJalloc->size;
//}
//}
