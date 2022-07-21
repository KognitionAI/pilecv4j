#pragma once

#include <mutex>

#include <Python.h>
#include "log.h"

namespace pilecv4j {
namespace python {
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

  // This should only be used when the GIL is already
  // being held.
  class ReleaseGilGuard {
    PyThreadState* _save;
  public:
    inline ReleaseGilGuard() : _save(nullptr) {
      _save = PyEval_SaveThread();
    }

    inline ~ReleaseGilGuard() {
      if (_save)
        PyEval_RestoreThread(_save);
    }
  };

  // This should only be used when the GIL is already
  // being held.
  template<class M> class GilSafeLockGuard {
    std::lock_guard<M>* guard;

  public:
    inline GilSafeLockGuard(M& mutex) : guard(nullptr) {
      ReleaseGilGuard rgg; // release the GIL
      guard = new std::lock_guard<M>(mutex);
    }

    inline ~GilSafeLockGuard() {
      if (guard)
        delete guard;
    }
  };

}
}
