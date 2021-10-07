#pragma once

#include <Python.h>
#include "log.h"

namespace pilecv4j {
  class CallPythonGuard
  {
    PyGILState_STATE state;
  public:

    inline CallPythonGuard() {
      log(DEBUG, "GIL Ensure");
      state = PyGILState_Ensure();
    }

    inline ~CallPythonGuard() {
      log(DEBUG, "GIL Release");
      PyGILState_Release(state);
    }
  };

}
