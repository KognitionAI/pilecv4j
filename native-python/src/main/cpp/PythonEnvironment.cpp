#include <mutex>

#include "GilGuard.h"
#include "PythonEnvironment.h"
#include "ImageSource.h"
#include "RunPythonFunction.h"
#define NPY_NO_DEPRECATED_API NPY_1_7_API_VERSION
#include <numpy/arrayobject.h>

//====================================================
static const char* errCodeStrings[] = {
    "OK",
    "Failed to install module.",
    "Main module was already loaded. You can only run a single script in a process space. Use functions.",
    "Error in Python. There should have been an error message from python.",
    "Couldn't open python script file.",
    "Couldn't open python script module.",
    "Couldn't find python function.",
    "Illegal Argument (likely the script returned something that wasn't a NumPy Array)",
    "Couldn't find python class.",
    "Couldn't instantiate python object",
    "Failed to insert value into the dictionary"

};
static const char* totallyUnknownError = "UNKNOWN ERROR";

#define NUM_ERROR_CODES 11

//====================================================

namespace pilecv4j {
  static PythonEnvironment* s_instance = nullptr;

  const char* getStatusMessage(uint32_t status) {
    if (status >= NUM_ERROR_CODES || status < 0)
      return totallyUnknownError;
    else
      return errCodeStrings[status];
  }


  PythonEnvironment::PythonEnvironment() : mainThreadState(nullptr), numpyImported(false)
  {
    log(INFO, "Initializing python...");

    // Initialize python
    Py_Initialize();

    // Initialize and acquire the GIL
    PyEval_InitThreads();

    // Get the main thread state for the current thread.
    mainThreadState = PyEval_SaveThread();
  }

  PythonEnvironment::~PythonEnvironment()
  {
    log(INFO, "Shutting down python...");

    // clean up - finalize python
    PyEval_RestoreThread(mainThreadState);

    // if we have the Module
    if (kogModule)
      Py_DECREF(kogModule);

    Py_Finalize();
  }

  int32_t PythonEnvironment::getFunctionFromModuleAtomic(const char* moduleName, const char* funcName, PyObject** ret) {
    *ret = nullptr;
    StatusCode statusCode = OK;

    GilSafeLockGuard<std::mutex> lck(envLock); // atomic. The GIL is release by python itself inside PyImport_Import

    // New reference
    PyObject *pModule = PythonEnvironment::instance()-> getModuleOrImport(moduleName);

    if (!pModule) {
      log (ERROR, "Failed to open the module %s", moduleName);
      statusCode = CANT_OPEN_PYTHON_MODULE;
      return statusCode;
    }

    // Load all module level attributes as a dictionary
    log(TRACE, "Loading dictionary for module %s", moduleName);
    // Borrowed reference
    PyObject *pDict = PyModule_GetDict(pModule);

    // New reference
    PyObject* pFuncName = PyUnicode_FromString(funcName);
    log(TRACE, "Looking for function %s in module %s", funcName, moduleName);
    // Borrowed reference
    PyObject *pFunc = PyDict_GetItem(pDict, pFuncName);
    Py_DECREF(pFuncName);

    *ret = pFunc;
    if(!pFunc){
      log(ERROR, "Couldn't find func %s in module %s", funcName, moduleName);
      statusCode = CANT_FIND_PYTHON_FUNCTION;
    }

    if (pModule)
      Py_DECREF(pModule);

    return statusCode;
  }

  PyObject* PythonEnvironment::getModuleOrImport(const char* moduleName) {
    // New reference
    PyObject* pName = PyUnicode_FromString(moduleName);
    log(TRACE, "Checking module %s", moduleName);
    PyObject *pModule;
    // New reference
    pModule = PyImport_GetModule(pName);
    if (!pModule) {
      log(TRACE, "Importing module %s", moduleName);
      // New reference
      pModule = PyImport_Import(pName);
      if (pModule)
        log(TRACE, "Imported module %s", moduleName);
      else {
        log(TRACE, "Failed to import module %s", moduleName);
        PyErr_Print();
      }
    } else {
      log(TRACE, "Module %s exists already", moduleName);
    }

    if (pName)
      Py_DECREF(pName);

    return pModule;
  }

//  PyObject* PythonEnvironment::getModuleOrNew(const char* moduleName) {
//    // New reference
//    PyObject* pName = PyUnicode_FromString(moduleName);
//    log(TRACE, "Checking module %s", moduleName);
//    PyObject *pModule;
//    // New reference
//    pModule = PyImport_GetModule(pName);
//    if (!pModule) {
//      log(TRACE, "Creating new module %s", moduleName);
//      // New reference
//      pModule = PyModule_New(moduleName);
//      if (pModule) {
//        log(TRACE, "Created new module %s", moduleName);
//        PyModule_AddStringConstant(pModule, "__file__", "");
//      }
//      else
//        log(TRACE, "Failed to create new module %s", moduleName);
//    } else {
//      log(TRACE, "Module %s exists already", moduleName);
//    }
//
//    if (pName)
//      Py_DECREF(pName);
//
//    return pModule;
//  }

  void PythonEnvironment::addModulePath(const char* filedir) {
    CallPythonGuard gg;

    PyObject* sysPath = PySys_GetObject("path");
    PyObject* dir = PyUnicode_FromString(filedir);

    // check to see if it's there anyway
    int sz = (int)PyList_Size(sysPath);
    log(TRACE, "Existing sys.path length %d", sz);
    bool foundMatch = false;
    for (int i = 0; i < sz; i++) {
      PyObject* cur = PyList_GET_ITEM(sysPath, i);
      log(TRACE, "Existing sys.path entry %s", (char*)PyUnicode_1BYTE_DATA(cur));
      PyObject* cmp = PyObject_RichCompare(dir, cur, Py_EQ);
      if (PyObject_IsTrue(cmp)) {
        foundMatch = true;
        Py_DECREF(cmp);
        break;
      }
      Py_DECREF(cmp);
    }

    if (!foundMatch) {
      log(INFO, "Adding \"%s\" to PYTHONPATH", filedir);
      PyList_Append(sysPath, dir);
    } else {
      log(TRACE, "The module path \"%s\" already exists in PYTHONPATH", filedir);
    }

    Py_DECREF(dir);
  }

  PythonEnvironment* PythonEnvironment::instance()
  {
    static std::mutex pyEnvMutex; // only used here.
    std::lock_guard<std::mutex> lck (pyEnvMutex);

    if (s_instance == nullptr)
      s_instance = new PythonEnvironment();

    return s_instance;
  }

  int32_t PythonEnvironment::runModel(const char* moduleName, const char* functionName, PyObject* paramDict) {
    CallPythonGuard gg;
    {
      RunPythonFunction func(moduleName, functionName, paramDict);
      PyObject* obj = func.execute();
      log(TRACE, "func %s returned object %ld", functionName, static_cast<long>((uint64_t)obj));
      if (obj) {
        if (obj == Py_None) {
          log(TRACE, "func %s returned object is None", functionName);
          Py_DECREF(obj);
          return func.statusCode;
        }
        log(TRACE, "decrementing return value (refCnt pre-dec: %d)", obj->ob_refcnt);
        Py_DECREF(obj);
      }
      return func.statusCode;
    }
  }
}

