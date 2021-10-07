#include "RunPythonFunction.h"

#include <iostream>
#include "ImageSource.h"

namespace pilecv4j {

  PyObject* RunPythonFunction::execute()
  {
    PyObject* result = nullptr;
    // New reference
    PyObject *pModule = PythonEnvironment::instanceX()-> getModuleOrImport(moduleName);

    if (!pModule) {
      log (ERROR, "Failed to open the module %s", moduleName);
      statusCode = CANT_OPEN_PYTHON_MODULE;
      goto endstuff;
    }

    {
      // Load all module level attributes as a dictionary
      log(TRACE, "Loading dictionary for module %s", moduleName);
      // Borrowed reference
      PyObject *pDict = PyModule_GetDict(pModule);

      // New reference
      PyObject* pFuncName = PyUnicode_FromString(funcName);
      log(TRACE, "Looking for function %s in module %s", funcName, moduleName);
      // Borrowed reference
      PyObject *pFunc = PyDict_GetItem(pDict, pFuncName);

      if(pFunc != NULL){
        log(DEBUG, "Calling model factory %s in module %s", funcName, moduleName );
        // New reference
        //result = PyObject_CallObject(pFunc, nullptr);
        PyObject* args = PyTuple_New(0);
        result = PyObject_Call(pFunc, args, paramDict);
        Py_DECREF(args);
      } else {
        log(ERROR, "Couldn't find func %s in module %s", funcName, moduleName);
        statusCode = CANT_FIND_PYTHON_FUNCTION;
      }
      Py_DECREF(pFuncName);
    }

    endstuff: {
      if (pModule)
        Py_DECREF(pModule);

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

