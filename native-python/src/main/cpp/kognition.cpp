
#include <stdint.h>
#include <mutex>
#include "common/jfloats.h"
#include "PythonEnvironment.h"
#include "RunPythonFunction.h"
#include "ImageSource.h"
#include "GilGuard.h"
#include "JavaHandle.h"

#include "common/kog_exports.h"
#include <cinttypes>

int initModule_kognition();

static bool inited = false;

using namespace pilecv4j::python;

//static void dumpDict(PyObject* dict) {
//  PyObject *key, *value;
//  Py_ssize_t pos = 0;
//  fprintf(stderr,"Dumping dict\n");
//  log(ERROR, "HERE1");
//  while (PyDict_Next(dict, &pos, &key, &value)) {
//    log(ERROR, "HERE2");
//    PyObject_Print(key, stderr, Py_PRINT_RAW);
//    log(ERROR, "HERE3");
//    fprintf(stderr, " = ");
//    log(ERROR, "HERE4");
//    PyObject_Print(value, stderr, Py_PRINT_RAW);
//    log(ERROR, "HERE5");
//    fprintf(stderr, "\n=============\n");
//    log(ERROR, "HERE6");
//    fflush(stderr);
//  }
//}
//
//static void dumpModuleDict(PyObject* module) {
//  fprintf(stderr,"Dumping dict for %s\n", PyModule_GetName(module));
//  PyObject* dict = PyModule_GetDict(module);
//  if (!dict) {
//    log(ERROR, "Failed to get dict from module %s", PyModule_GetName(module));
//    return;
//  }
//  dumpDict(dict);
//}

// this is in module.cpp
extern PyObject* convert(JavaHandle* pt);

#define COMPONENT "JAPI"
#define PILECV4J_TRACE RAW_PILECV4J_TRACE(COMPONENT)

extern "C" {
  // ==============================================================
  // Global python management
  // ==============================================================
  KAI_EXPORT int32_t pilecv4j_python_initPython() {
    PILECV4J_TRACE;
    static std::mutex initMutex;

    log(DEBUG, "initPython called from java.");
    std::lock_guard<std::mutex> lck(initMutex);

    if (inited) {
      log(WARN, "Attempted call to initialize python more than once.");
      return OK;
    }

    // needs to be called before creating PythonEnvironment
    if (initModule_kognition() < 0)
      return (int32_t) FAILED_INSTALL_KOGNITION_MODULE;

    // This will instantiate a PythonEnvironment
    PythonEnvironment::instance();

    inited = true;
    return (int32_t)OK;
  }

  KAI_EXPORT int32_t pilecv4j_python_runPythonFunction(const char* moduleName, const char* functionName, uint64_t ptupleArgs,
      uint64_t paramDictRef, void** resultBuf, int* resultSize) {
    PILECV4J_TRACE;

    PyObject* paramDict = (PyObject*)paramDictRef;
    PyObject* tupleArgs = (PyObject*)ptupleArgs;
    PythonEnvironment* p = PythonEnvironment::instance();

//    if (isEnabled(TRACE))
//      dumpDict(paramDict);
    PyObject* pyObjResult = nullptr;

    int32_t ret = p->runFunction(moduleName, functionName, tupleArgs, paramDict, &pyObjResult);

    if (isEnabled(TRACE))
      log(TRACE, "Result obj %" PRId64 " with refcnt: %d", (uint64_t)pyObjResult, (int)(pyObjResult ? pyObjResult->ob_refcnt : -1));

    if (ret != OK) {
      if (pyObjResult) {
        CallPythonGuard gg;
        Py_DECREF(pyObjResult);
      }
      *resultBuf = nullptr;
      return ret;
    }

    // if there is no result there was a python error which should have been printed already
    if (!pyObjResult) {
      *resultBuf = nullptr;
      return PYTHON_ERROR;
    }

    // if there was a return of 'None' then return null.
    if (pyObjResult == Py_None) {
      if (pyObjResult) {
        CallPythonGuard gg;
        Py_DECREF(pyObjResult);
      }
      *resultBuf = nullptr;
      return OK;
    }

    // now we need to parse the results.
    if (isEnabled(TRACE))
      log(TRACE, "Result obj, pre-parsing: %" PRId64 " with refcnt: %d", (uint64_t)pyObjResult, (int)(pyObjResult ? pyObjResult->ob_refcnt : -1));

    ret = RunPythonFunction::parseFunctionReturn(pyObjResult, resultBuf, resultSize);
    {
      CallPythonGuard gg;
      Py_DECREF(pyObjResult);
    }

    return OK;
  }

  KAI_EXPORT int32_t pilecv4j_python_freeFunctionResults(void* results) {
    PILECV4J_TRACE;
    if (isEnabled(TRACE))
      log(TRACE, "freeing results at %" PRId64, (uint64_t)results);
    if (results) {
      return RunPythonFunction::freeFuntionReturn(results);
    }
    return OK;
  }

  KAI_EXPORT void pilecv4j_python_addModulePath(const char* modPath) {
    PILECV4J_TRACE;
    PythonEnvironment::instance()->addModulePath(modPath);
  }

  KAI_EXPORT void pilecv4j_python_pyObject_decref(uint64_t nativeRef) {
    PILECV4J_TRACE;
    if (nativeRef) {
      PyObject* pyo = (PyObject*)nativeRef;
      if (isEnabled(TRACE))
        log(TRACE, "decrementing refcnt on %" PRId64 " with a pre-dec refcnt: %d", (uint64_t)nativeRef, (int)pyo->ob_refcnt);
      CallPythonGuard gg;
      Py_DECREF(pyo);
    }
  }

  KAI_EXPORT void pilecv4j_python_pyObject_incref(uint64_t nativeRef) {
    PILECV4J_TRACE;
    if (nativeRef) {
      PyObject* pyo = (PyObject*)nativeRef;
      if (isEnabled(TRACE))
        log(TRACE, "incrementing refcnt on %" PRId64 " with a pre-inc refcnt: %d", (uint64_t)nativeRef, (int)pyo->ob_refcnt);
      CallPythonGuard gg;
      Py_INCREF(pyo);
    }
  }

  // ==============================================================
  // KogSys lifecycle and methods
  // ==============================================================
  KAI_EXPORT uint64_t pilecv4j_python_kogSys_create(get_image_source cb) {
    PILECV4J_TRACE;
    return (uint64_t)new JavaHandle(cb);
  }

  KAI_EXPORT int32_t pilecv4j_python_kogSys_numModelLabels(uint64_t ptRef) {
    PILECV4J_TRACE;
    JavaHandle* ths = (JavaHandle*)ptRef;
    return ths->getNumLabels();
  }

  KAI_EXPORT const char* pilecv4j_python_kogSys_modelLabel(uint64_t ptRef, int32_t index) {
    PILECV4J_TRACE;
    JavaHandle* ths = (JavaHandle*)ptRef;
    return ths->getModelLabel(index);
  }

  KAI_EXPORT int32_t pilecv4j_python_kogSys_destroy(uint64_t ptRef) {
    PILECV4J_TRACE;
    delete (JavaHandle*)ptRef;
    return OK;
  }

  // ==============================================================
  // ImageSource lifecycle and methods
  // ==============================================================
  KAI_EXPORT uint64_t pilecv4j_python_imageSource_create(uint64_t pt) {
    PILECV4J_TRACE;
    JavaHandle* ptorch = (JavaHandle*)pt;
    return uint64_t(new ImageSource());
  }

  KAI_EXPORT void pilecv4j_python_imageSource_destroy(uint64_t imageSourceRef) {
    PILECV4J_TRACE;
    CallPythonGuard gg; // KogMatWithResults will be Py_DECREF params
    ImageSource* is = ((ImageSource*)imageSourceRef);
    delete is;
  }

  KAI_EXPORT uint64_t pilecv4j_python_imageSource_send(uint64_t imageSourceRef, uint64_t dictRef, uint64_t matRef, int32_t rgbi) {
    PILECV4J_TRACE;
    ImageSource* is = ((ImageSource*)imageSourceRef);
    if (matRef == 0L) {
      is->send(nullptr);
      return 0L;
    }
    PyObject* params = (PyObject*)dictRef;
    cv::Mat* mat = (cv::Mat*)matRef;
    CallPythonGuard gg; // KogMatWithResults will be Py_INCREF/Py_DECREF params
    KogMatWithResults* km = new KogMatWithResults(mat, rgbi ? true : false, false, params);
    is->send(km);
    return (uint64_t)km;
  }

  KAI_EXPORT uint64_t pilecv4j_python_imageSource_peek(uint64_t imageSourceRef) {
    PILECV4J_TRACE;
    ImageSource* is = ((ImageSource*)imageSourceRef);
    return (uint64_t)(is->peek());
  }

  // ==============================================================
  // KogMatResults lifecycle and methods
  // ==============================================================

  KAI_EXPORT void pilecv4j_python_kogMatResults_destroy(uint64_t nativeObj) {
    PILECV4J_TRACE;
    log(TRACE, "Closing KogMatWithResults at %ld", (long)nativeObj);
    if (nativeObj) {
      CallPythonGuard gg; // KogMatWithResults may be Py_INCREF/Py_DECREF params
      ((KogMatWithResults*)nativeObj)->decrement();
    }
  }

  KAI_EXPORT int32_t pilecv4j_python_kogMatResults_hasResult(uint64_t nativeObj) {
    PILECV4J_TRACE;
    log(TRACE, "hasResult on %ld", (long)(nativeObj));

    if (nativeObj)
      return ((KogMatWithResults*)nativeObj)->resultsSet ? 1 : 0;
    else
      return 0;
  }

  KAI_EXPORT int32_t pilecv4j_python_kogMatResults_isAbandoned(uint64_t nativeObj) {
    PILECV4J_TRACE;
    if (nativeObj)
      return ((KogMatWithResults*)nativeObj)->abandoned ? 1 : 0;
    else
      return 0;
  }

  KAI_EXPORT uint64_t pilecv4j_python_kogMatResults_getResults(uint64_t nativeObj) {
    PILECV4J_TRACE;
    if (nativeObj) {
      cv::Mat* results = ((KogMatWithResults*)nativeObj)->results;
      if (results)
        return (uint64_t)(new cv::Mat(*results));
      else
        return 0L;
    } else
      return 0L;
  }

  // ==============================================================
  // Python Dict lifecycle and methods
  // ==============================================================
  KAI_EXPORT uint64_t pilecv4j_python_dict_create() {
    PILECV4J_TRACE;
    CallPythonGuard gg;
    return (uint64_t)(PyObject*) PyDict_New();
  }

  KAI_EXPORT void pilecv4j_python_dict_destroy(uint64_t dictRef) {
    PILECV4J_TRACE;
    CallPythonGuard gg;
    Py_DECREF((PyObject*)dictRef);
  }

  KAI_EXPORT int32_t pilecv4j_python_dict_putBoolean(uint64_t dictRef, const char* key, int32_t valRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* dict = (PyObject*)dictRef;
    PyObject* val = valRef ? Py_True : Py_False;
    Py_INCREF(val);
    if (PyDict_SetItemString(dict, key, val) != 0) {
      log(ERROR, "Failed to insert parameter (%s : %s) into dictionary", key, (valRef ? "True" : "False"));
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    Py_DECREF(val);
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_dict_putMat(uint64_t dictRef, const char* key, uint64_t matRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* dict = (PyObject*)dictRef;
    PyObject* val;
    if (matRef) {
      cv::Mat* mat = (cv::Mat*)matRef;
      int resultInt = OK;
      val = ImageSource::convertMatToNumPyArray(mat, SHALLOW_COPY, &resultInt, false);
      if (resultInt != OK) {
        log(ERROR, "Failed to convert mat at (%" PRId64 ") to a numpy array", (uint64_t)matRef);
        Py_DECREF(val);
        return resultInt;
      }
    } else {
      val = Py_None;
      Py_INCREF(val);
    }
    if (PyDict_SetItemString(dict, key, val) != 0) {
      log(ERROR, "Failed to insert mat parameter (%s : %" PRId64 ") into dictionary", key, (uint64_t)matRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    Py_DECREF(val);
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_dict_putPyObject(uint64_t dictRef, const char* key, uint64_t pyObRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* dict = (PyObject*)dictRef;
    PyObject* val;
    if (pyObRef) {
      val = (PyObject*)pyObRef;
    } else {
      val = Py_None;
      Py_INCREF(val);
    }
    if (PyDict_SetItemString(dict, key, val) != 0) {
      log(ERROR, "Failed to insert pyobj parameter (%s : %" PRId64 ") into dictionary", key, (uint64_t)pyObRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    return (int32_t)result;
  }


  KAI_EXPORT int32_t pilecv4j_python_dict_putInt(uint64_t dictRef, const char* key, int64_t valRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* dict = (PyObject*)dictRef;
    PyObject* val = PyLong_FromLongLong((long long)valRef);
    if (PyDict_SetItemString(dict, key, val) != 0) {
      log(ERROR, "Failed to insert parameter (%s : %d) into dictionary", key, (int)valRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    Py_DECREF(val);
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_dict_putFloat(uint64_t dictRef, const char* key, float64_t valRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* dict = (PyObject*)dictRef;
    PyObject* val = PyFloat_FromDouble((double)valRef);
    if (PyDict_SetItemString(dict, key, val) != 0) {
      log(ERROR, "Failed to insert parameter (%s : %f) into dictionary", key, (float)valRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    Py_DECREF(val);
    return (int32_t)result;
  }


  KAI_EXPORT int32_t pilecv4j_python_dict_putString(uint64_t dictRef, const char* key, const char* valRaw) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* dict = (PyObject*)dictRef;
    PyObject* val = PyUnicode_FromString(valRaw);
    if (PyDict_SetItemString(dict, key, val) != 0) {
      log(ERROR, "Failed to insert parameter (%s : %s) into dictionary", key, valRaw);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    Py_DECREF(val);
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_dict_putKogSys(uint64_t dictRef, const char* key, uint64_t valRef) {
    PILECV4J_TRACE;
    if (isEnabled(TRACE))
      log(TRACE, "adding JavaHandle(%" PRId64 ") to dictionary at %s", valRef, key);

    CallPythonGuard gg;
    PythonEnvironment::instance()->loadKognitionModule();
    PyObject* pytorch = convert((JavaHandle*)valRef);
    if (!pytorch) {
      log (ERROR, "Failed to convert a JavaHandle instance to a PyObject");
      return CANT_INSTANTIATE_PYTHON_OBJECT;
    }

    int result = OK;

    PyObject* dict = (PyObject*)dictRef;
    if (PyDict_SetItemString(dict, key, pytorch) != 0) {
      log(ERROR, "Failed to insert parameter (%s : %" PRId64 ") into dictionary", key, valRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
    }
    Py_DECREF(pytorch);
    return result;
  }

  // ==============================================================
  // Python Tuple lifecycle and methods
  // ==============================================================
  KAI_EXPORT uint64_t pilecv4j_python_tuple_create(int32_t size) {
    PILECV4J_TRACE;
    CallPythonGuard gg;
    return (uint64_t)(PyObject*) PyTuple_New(size);
  }

  KAI_EXPORT void pilecv4j_python_tuple_destroy(uint64_t tupleRef) {
    PILECV4J_TRACE;
    CallPythonGuard gg;
    Py_DECREF((PyObject*)tupleRef);
  }

  KAI_EXPORT int32_t pilecv4j_python_tuple_putBoolean(uint64_t tupleRef, int32_t index, int32_t valRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* tuple = (PyObject*)tupleRef;
    PyObject* val = valRef ? Py_True : Py_False;
    Py_INCREF(val);
    if (PyTuple_SetItem(tuple, index, val) != 0) {
      log(ERROR, "Failed to insert parameter (%s) into tuple at %d", (valRef ? "True" : "False"), (int)index);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(val); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_tuple_putMat(uint64_t tupleRef, int32_t index, uint64_t matRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* tuple = (PyObject*)tupleRef;
    PyObject* val;
    if (matRef) {
      cv::Mat* mat = (cv::Mat*)matRef;
      int resultInt = OK;
      val = ImageSource::convertMatToNumPyArray(mat, SHALLOW_COPY, &resultInt, false);
      if (resultInt != OK) {
        log(ERROR, "Failed to convert mat at (%" PRId64 ") to a numpy array", (uint64_t)matRef);
        Py_DECREF(val);
        return result;
      }
    } else {
      val = Py_None;
      Py_INCREF(val);
    }
    if (PyTuple_SetItem(tuple, index, val) != 0) {
      log(ERROR, "Failed to insert mat parameter (%d : %" PRId64 ") into dictionary", (int)index, (uint64_t)matRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(val); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_tuple_putPyObject(uint64_t tupleRef, int32_t index, uint64_t pyObRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* tuple = (PyObject*)tupleRef;
    PyObject* val;
    if (pyObRef) {
      val = (PyObject*)pyObRef;
    } else {
      val = Py_None;
    }
    Py_INCREF(val);
    if (PyTuple_SetItem(tuple, index, val) != 0) {
      log(ERROR, "Failed to insert pyobject parameter (%d : %" PRId64 ") into dictionary", (int)index, (uint64_t)pyObRef);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(val); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return (int32_t)result;
  }


  KAI_EXPORT int32_t pilecv4j_python_tuple_putInt(uint64_t tupleRef, int32_t index, int64_t valRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* tuple = (PyObject*)tupleRef;
    PyObject* val = PyLong_FromLongLong((long long)valRef);
    if (PyTuple_SetItem(tuple, index, val) != 0) {
      log(ERROR, "Failed to insert parameter (%ld) into tuple at %d", (long)valRef, (int)index);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(val); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_tuple_putFloat(uint64_t tupleRef, int32_t index, float64_t valRef) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* tuple = (PyObject*)tupleRef;
    PyObject* val = PyFloat_FromDouble((double)valRef);
    if (PyTuple_SetItem(tuple, index, val) != 0) {
      log(ERROR, "Failed to insert parameter (%f) into tuple at %d", (float)valRef, (int)index);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(val); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return (int32_t)result;
  }


  KAI_EXPORT int32_t pilecv4j_python_tuple_putString(uint64_t tupleRef, int32_t index, const char* valRaw) {
    PILECV4J_TRACE;
    StatusCode result = OK;
    CallPythonGuard gg;
    PyObject* tuple = (PyObject*)tupleRef;
    PyObject* val = PyUnicode_FromString(valRaw);
    if (PyTuple_SetItem(tuple, index, val) != 0) {
      log(ERROR, "Failed to insert parameter (%s) into tuple at %d", valRaw, (int)index);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(val); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return (int32_t)result;
  }

  KAI_EXPORT int32_t pilecv4j_python_tuple_putKogSys(int64_t tupleRef, int32_t index, uint64_t valRef) {
    PILECV4J_TRACE;
    CallPythonGuard gg;
    PythonEnvironment::instance()->loadKognitionModule();
    PyObject* pytorch = convert((JavaHandle*)valRef);
    if (!pytorch) {
      log (ERROR, "Failed to convert a PyTorch instance to a PyObject");
      return CANT_INSTANTIATE_PYTHON_OBJECT;
    }

    int result = OK;

    PyObject* tuple = (PyObject*)tupleRef;
    if (PyTuple_SetItem(tuple, index, pytorch) != 0) {
      log(ERROR, "Failed to insert parameter (JavaHandle: %" PRId64 ") into tuple at %d", valRef, (int)index);
      result = FAILED_TO_INSERT_INTO_DICTIONARY;
      Py_DECREF(pytorch); // PyTuple_SetItem steals a reference while PyDict_SetItemString does not
    }
    return result;
  }

  // ==============================================================
  // Status/Error code access
  // ==============================================================
  KAI_EXPORT char* pilecv4j_python_status_message(uint32_t status) {
    PILECV4J_TRACE;
    if (status == 0)
      return nullptr;

    return strdup(getStatusMessage(status));
  }

  KAI_EXPORT void pilecv4j_python_status_freeMessage(char* messageRef) {
    PILECV4J_TRACE;
    if (messageRef)
      free((void*)messageRef);
  }

  // ==============================================================
  // Logging
  // ==============================================================
  KAI_EXPORT int32_t pilecv4j_python_setLogLevel(int32_t plogLevel) {
    PILECV4J_TRACE;
    if (plogLevel <= MAX_LOG_LEVEL && plogLevel >= 0)
      setLogLevel(static_cast<LogLevel>(plogLevel));
    else
      setLogLevel(FATAL);

    return OK;
  }
}

