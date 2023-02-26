#include "RunPythonFunction.h"

#include <iostream>
#include "ImageSource.h"

#include "common/jfloats.h"

namespace pilecv4j {
namespace python {

#define COMPONENT "RUNP"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

#define RESULT_BLOCK_SIZE_INCREMENT 8192

  static inline std::size_t do_write(const uint8_t* src, uint8_t* dst, std::size_t pos, std::size_t numBytes) {
    PILECV4J_TRACE;
    for (int i = 0; i < numBytes; i++) dst[pos++] = src[i];
    return pos;
  }

  template <typename T> union Byteable {
    inline Byteable(T pval) : val(pval) { }
    inline Byteable() : val(0) {}
    T val;
    uint8_t bytes[sizeof(T)];
  } ;

  static uint8_t* ensure(uint8_t* buf, std::size_t pos, std::size_t& capacity, std::size_t numBytes) {
    PILECV4J_TRACE;
    if ((pos + numBytes) > capacity) {
      capacity += RESULT_BLOCK_SIZE_INCREMENT;
      return (uint8_t*)std::realloc(buf, capacity);
    }
    else
      return buf;
  }

  template <typename U> static uint8_t* writeResultAndUnion(const U& un, uint8_t resultType, uint8_t* buf, std::size_t& capacity, std::size_t& pos) {
    PILECV4J_TRACE;
    std::size_t sz = sizeof(un.bytes);
    buf = ensure(buf, pos, capacity, sz + 1); // include the size of the resultType
    buf[pos++] = resultType;
    pos = do_write(un.bytes, buf, pos, sz);
    return buf;
  }

  template <typename U> static uint8_t* writeUnion(const U& un, uint8_t* buf, std::size_t& capacity, std::size_t& pos) {
    PILECV4J_TRACE;
    std::size_t sz = sizeof(un.bytes);
    buf = ensure(buf, pos, capacity, sz + 1); // include the size of the resultType
    pos = do_write(un.bytes, buf, pos, sz);
    return buf;
  }

  template <typename U> static void fetch(uint8_t const* buf, U& un) {
    PILECV4J_TRACE;
    std::size_t sz = sizeof(un.bytes);
    memcpy(un.bytes, buf, sz);
  }

  static uint8_t* write(PyObject* toWrite, uint8_t* buf, std::size_t& pos, std::size_t& capacity, int32_t& resultStatus) {
    PILECV4J_TRACE;
    const bool traceOn = isEnabled(TRACE);
    resultStatus = OK;
    int retRes = 1;
    if (PyLong_Check(toWrite)) {
      if (traceOn)
        log(TRACE, "Converting PyObject result to long");
      Byteable<int64_t> value ((int64_t)PyLong_AsLong(toWrite));
      buf = writeResultAndUnion(value, (uint8_t)PyResultLONG, buf, capacity, pos);
    } else if (PyFloat_Check(toWrite)) {
      if (traceOn)
        log(TRACE, "Converting PyObject result to double");
      Byteable<float64_t> value ((float64_t)PyFloat_AsDouble(toWrite));
      buf = writeResultAndUnion(value, (uint8_t)PyResultFLOAT, buf, capacity, pos);
    } else if (PyUnicode_Check(toWrite)) {
      if (traceOn)
        log(TRACE, "Converting PyObject result to string");
      Py_ssize_t size = 0;
      const char* str = PyUnicode_AsUTF8AndSize(toWrite, &size);
      if (traceOn)
        log(TRACE, "Converted PyObject resulting string is: %s", str);
      buf = ensure(buf, pos, capacity, 1 + size + 4);
      buf[pos++] = (uint8_t)PyResultSTRING; // 1
      Byteable<int32_t> sizeToWrite = size;
      writeUnion(sizeToWrite, buf, capacity, pos); // 4
      memcpy(buf + pos, str, size); // size
      pos += size;
    } else if (ImageSource::isNumpyArray(toWrite, &retRes)) {
      int lresultStat;
      cv::Mat* mat = ImageSource::convertNumPyArrayToMat(toWrite, DEEP_COPY, &lresultStat, false);
      if (lresultStat != OK) {
        resultStatus = (int)lresultStat;
        if (mat)
          delete mat;
        return buf;
      }

      if (traceOn)
        log(TRACE, "Converted PyObject/Numpy array to cv::Mat* is: %" PRId64, (uint64_t)mat);

      Byteable<void*> matPtr = mat;
      buf = writeResultAndUnion(matPtr, PyResultMAT, buf, capacity, pos);
    } else if (PyList_Check(toWrite)) {
      Py_ssize_t len = PyList_Size(toWrite);
      Byteable<int32_t> listSize = (int32_t)len;
      buf = writeResultAndUnion(listSize, PyResultLIST, buf, capacity, pos);
      resultStatus = OK;
      {
        CallPythonGuard gg;
        for (Py_ssize_t i = 0; i < len && resultStatus == OK; i++) {
          PyObject *item = PyList_GetItem(toWrite, i);
          ReleaseGilGuard rgg;
          if (!item) {
            resultStatus = ILLEGAL_ARGUMENT;
            return buf;
          }
          write(item ? item : Py_None, buf, pos, capacity, resultStatus);
        }
      }
      return buf;
    } else if (PyTuple_Check(toWrite)) {
      Py_ssize_t len = PyTuple_Size(toWrite);
      Byteable<int32_t> listSize = (int32_t)len;
      buf = writeResultAndUnion(listSize, PyResultLIST, buf, capacity, pos);
      resultStatus = OK;
      {
        CallPythonGuard gg;
        for (Py_ssize_t i = 0; i < len && resultStatus == OK; i++) {
          PyObject *item = PyTuple_GetItem(toWrite, i);
          ReleaseGilGuard rgg;
          if (!item) {
            resultStatus = ILLEGAL_ARGUMENT;
            return buf;
          }
          write(item ? item : Py_None, buf, pos, capacity, resultStatus);
        }
      }
      return buf;
    } else if (toWrite == Py_None) {
      buf = ensure(buf, pos, capacity, 1);
      buf[pos++] = (uint8_t)PyResultNONE;
    } else {
      if (retRes != OK) {
        resultStatus = retRes;
        log(ERROR, "Failed to check if pyobject is a numpy array. Perhaps a failure to import the numpy module.");
        return buf;
      }
      if (traceOn)
        log(TRACE, "Converting PyObject result to pointer");
      Byteable<void*> value (toWrite);
      Py_INCREF(toWrite);
      buf = writeResultAndUnion(value, (uint8_t)PyResultPyObject, buf, capacity, pos);
    }
    return buf;
  }

  static int32_t dofreeFuntionReturn(uint8_t * buf, std::size_t* count, bool doFree) {
    PILECV4J_TRACE;
    std::size_t& pos = *count;
    uint8_t resultType = buf[pos++];
    switch ((ResultType)resultType) {
      case PyResultNONE: {
        if (doFree)
          std::free(buf);
        return OK;
      }
      case PyResultLONG:
      case PyResultFLOAT: {
        pos += sizeof(float64_t); // sizeof float64_t == sizeof int64_t
        if (doFree)
          std::free(buf);
        return OK;
      }
      case PyResultSTRING: {
        Byteable<int32_t> value;
        fetch(buf + pos, value);
        pos += sizeof(int32_t);
        pos += value.val;
        if (doFree)
          std::free(buf);
        return OK;
      }
      case PyResultMAT: {
        Byteable<void*> value;
        fetch(buf + 1, value);
        cv::Mat* mat = (cv::Mat*)value.val;
        if (mat)
          delete mat;
        if (doFree)
          std::free(buf);
        return OK;
      }
      case PyResultPyObject: {
        Byteable<void*> value;
        fetch(buf + pos, value);
        pos += sizeof(void*);
        PyObject* pyO = (PyObject*)value.val;
        if (pyO) {
          if (isEnabled(TRACE))
            log(TRACE, "decrementing refcnt on %" PRId64 " with a pre-dec refcnt: %d", (uint64_t)pyO, (int)pyO->ob_refcnt);
          CallPythonGuard gg;
          Py_DECREF(pyO);
        }
        if (doFree)
          std::free(buf);
        return OK;
      }
      case PyResultLIST : {
        Byteable<int32_t> value;
        fetch(buf + pos, value);
        pos += sizeof(int32_t);
        int res = OK;
        for (std::size_t i = 0; i < value.val; i++) {
          res = dofreeFuntionReturn(buf, count, false);
          if (res != OK)
            return res;
        }
        if (doFree)
          std::free(buf);
        return OK;
      }
      default: {
        log(ERROR, "There as an unrecognized return type (%d) in the result buffer being freed", (int)resultType);
        return BAD_DATA;
      }
    }
  }

  int32_t RunPythonFunction::parseFunctionReturn(PyObject* results, void** parsedResults, int* resultSize) {
    PILECV4J_TRACE;
    *resultSize = 0;
    *parsedResults = nullptr;
    // figure out the size of the results block.
    std::size_t capacity = RESULT_BLOCK_SIZE_INCREMENT;
    uint8_t* ret = (uint8_t*)std::malloc(capacity);
    std::size_t pos = 0;
    int32_t resultStat = OK;
    if (isEnabled(TRACE))
      log(TRACE, "Result obj pre-writing %" PRId64 " with refcnt: %d", (uint64_t)results, (int)(results ? results->ob_refcnt : -1));
    ret = write(results, ret, pos, capacity, resultStat);
    *parsedResults = ret;
    *resultSize = pos;
    return resultStat;
  }

  int32_t RunPythonFunction::freeFuntionReturn(void* bufv) {
    PILECV4J_TRACE;
    uint8_t * buf = (uint8_t*)bufv;
    std::size_t count = 0;
    return dofreeFuntionReturn(buf, &count, true);
  }

  PyObject* RunPythonFunction::execute()
  {
    PILECV4J_TRACE;
    PyObject* result = nullptr;
    PyObject *pFunc = nullptr;

    // pFunc is a borrowed reference
    statusCode = PythonEnvironment::instance()-> getFunctionFromModuleAtomic(moduleName, funcName, &pFunc);

    if (statusCode != OK) {
      log(ERROR, "Couldn't retrieve func %s from module %s", funcName, moduleName);
      return result;
    }

    // this shouldn't be possible if getFunctionFromModuleAtomic is written correctly
    if (!pFunc) {
      statusCode = CANT_FIND_PYTHON_FUNCTION;
      log(ERROR, "Couldn't retrieve func %s from module %s (2)", funcName, moduleName);
      return result;
    }

    log(DEBUG, "Calling function %s in module %s", funcName, moduleName );
    // New reference
    PyObject* args;
    if (tupleArgs) {
      args = tupleArgs;
      Py_INCREF(args);
    } else
      args = PyTuple_New(0);
    result = PyObject_Call(pFunc, args, paramDict);
    Py_DECREF(args);

    // did an error occurr in the script?
    if(PyErr_Occurred())
    {
      log(ERROR, "Script failed!");
      statusCode = PYTHON_ERROR;
      PyErr_Print();
      if (result)
        Py_DECREF(result);
      return NULL;
    }
    return result;
  }
}
}

