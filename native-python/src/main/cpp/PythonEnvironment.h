#pragma once

#include <mutex>

#include <opencv2/core/mat.hpp>
#include <Python.h>

#include "GilGuard.h"
#include "KogSystem.h"
#include "log.h"

#define KOGNITION_MODULE "pilecv4j"

namespace pilecv4j {
namespace python {

  enum StatusCode {
    OK = 0,
    FAILED_INSTALL_KOGNITION_MODULE = 1,
    MAIN_MODULE_ALREADY_EXISTS = 2,
    PYTHON_ERROR = 3,
    CANT_OPEN_PYTHON_FILE = 4,
    CANT_OPEN_PYTHON_MODULE = 5,
    CANT_FIND_PYTHON_FUNCTION = 6,
    ILLEGAL_ARGUMENT = 7,
    CANT_FIND_PYTHON_CLASS = 8,
    CANT_INSTANTIATE_PYTHON_OBJECT = 9,
    FAILED_TO_INSERT_INTO_DICTIONARY = 10
  };

  const char* getStatusMessage(uint32_t status);

  struct PythonEnvironment
  {
    PyThreadState* mainThreadState;
    bool numpyImported;

    PythonEnvironment();
    virtual ~PythonEnvironment();

    void addModulePath(const char* moduleDir);

    // GIL must be Ensured already
    int32_t getFunctionFromModuleAtomic(const char* moduleName, const char* funcName, PyObject** callable);

    int32_t runModel(const char* moduleName, const char* functionName, PyObject* paramDict);

    // GIL must be Ensured already
    inline void loadKognitionModule() {
      GilSafeLockGuard<std::mutex> lck(envLock);
      if (kogModule)
        return;
      kogModule = getModuleOrImport(KOGNITION_MODULE);
    }

    /**
     * Get the singleton instance of the PythonEnvironment which should initialize
     * Python itself.
     */
    static PythonEnvironment* instance();

  private:
    std::mutex envLock;
    PyObject* kogModule = nullptr;

    // GIL must be Ensured already
    PyObject* getModuleOrImport(const char * moduleName);

//    // GIL must be Ensured already
//    PyObject* getModuleOrNew(const char * moduleName);

  };
}
}

