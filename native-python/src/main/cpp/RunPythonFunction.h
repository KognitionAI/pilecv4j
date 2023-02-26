#pragma once

#include <Python.h>

namespace pilecv4j {
namespace python {

  enum ResultType {
    PyResultNONE = 0,
    PyResultLONG = 1,
    PyResultFLOAT = 2,
    PyResultSTRING = 3,
    PyResultMAT = 4,
    PyResultPyObject = 5,
    PyResultLIST = 6,
    PyResultMAP = 7,
  };

  class RunPythonFunction {
    const char * moduleName;
    const char * funcName;
    PyObject* tupleArgs;
    PyObject* paramDict;

  public:
    int32_t statusCode = 0;

    inline RunPythonFunction(const char* pModuleName,
                    const char* pFuncName,
                    PyObject* ptupleArgs,
                    PyObject* pparamDict) :
       moduleName(pModuleName),
       funcName(pFuncName),
       tupleArgs(ptupleArgs),
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

    static int32_t parseFunctionReturn(PyObject* results, void**, int*);
    static int32_t freeFuntionReturn(void*);

  };
}
}

