#include "RunPythonFunction.h"

#include <iostream>
#include "ImageSource.h"

namespace pilecv4j {

  PyObject* RunPythonFunction::execute()
  {
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

    log(DEBUG, "Calling model factory %s in module %s", funcName, moduleName );
    // New reference
    PyObject* args = PyTuple_New(0);
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

