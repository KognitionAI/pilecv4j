#pragma once

#include <Python.h>

namespace pilecv4j {
namespace python {

  class RunPythonFunction {
    const char * moduleName;
    const char * funcName;
    PyObject* paramDict;

  public:
    int32_t statusCode = 0;

    inline RunPythonFunction(const char* pModuleName,
                    const char* pFuncName,
                    PyObject* pparamDict) :
       moduleName(pModuleName),
       funcName(pFuncName),
       paramDict(pparamDict) {
      if (paramDict)
        Py_INCREF(paramDict);
    }

    virtual inline ~RunPythonFunction() {
      if (paramDict)
        Py_DECREF(paramDict);
    }

    RunPythonFunction(RunPythonFunction& ) = delete;

    virtual PyObject* execute();

  };
}
}

