#include <stdarg.h>
#include <stdlib.h>

#include "tensorflow/c/c_api.h"
#include "com_jiminger_image_TensorUtils.h"

static void deallocate_buffer(void* data, size_t len, void* arg) {
}

void throwException(JNIEnv* env, const char* clazz, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  // Using vsnprintf() instead of vasprintf() because the latter doesn't seem to
  // be easily available on Windows.
  const size_t max_msg_len = 512;
  char* message = static_cast<char*>(malloc(max_msg_len));
  if (vsnprintf(message, max_msg_len, fmt, args) >= 0) {
    env->ThrowNew(env->FindClass(clazz), message);
  } else {
    env->ThrowNew(env->FindClass(clazz), "");
  }
  free(message);
  va_end(args);
}

JNIEXPORT jlong JNICALL Java_com_jiminger_image_TensorUtils_createNativeTensorFromAddress
(JNIEnv * env, jclass, jint dtype, jlongArray shape, jlong sizeInBytes, jlong nativeDataPointer)
{
  int num_dims = static_cast<int>(env->GetArrayLength(shape));
  jlong* dims = nullptr;
  if (num_dims > 0) {
    jboolean is_copy;
    dims = env->GetLongArrayElements(shape, &is_copy);
  }
  static_assert(sizeof(jlong) == sizeof(int64_t),
                "Java long is not compatible with the TensorFlow C API");
  // On some platforms "jlong" is a "long" while "int64_t" is a "long long".
  //
  // Thus, static_cast<int64_t*>(dims) will trigger a compiler error:
  // static_cast from 'jlong *' (aka 'long *') to 'int64_t *' (aka 'long long
  // *') is not allowed
  //
  // Since this array is typically very small, use the guaranteed safe scheme of
  // creating a copy.
  int64_t* dims_copy = new int64_t[num_dims];
  for (int i = 0; i < num_dims; ++i) {
    dims_copy[i] = static_cast<int64_t>(dims[i]);
  }
  TF_Tensor* t = TF_NewTensor(static_cast<TF_DataType>(dtype), dims_copy, num_dims,
                              (void*) nativeDataPointer, static_cast<size_t>(sizeInBytes),
                              deallocate_buffer,nullptr);
  delete[] dims_copy;
  if (dims != nullptr) {
    env->ReleaseLongArrayElements(shape, dims, JNI_ABORT);
  }
  if (t == nullptr) {
    throwException(env, "java/lang/NullPointerException",
                   "unable to allocate memory for the Tensor");
    return 0;
  }
  return reinterpret_cast<jlong>(t);
}
