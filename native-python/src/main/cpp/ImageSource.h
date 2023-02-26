#pragma once
#include <mutex>

#include <opencv2/core/mat.hpp>
#include "PythonEnvironment.h"
#include <atomic>
#include <Python.h>

namespace pilecv4j {
namespace python {

  struct KogMatWithResults {
    cv::Mat* mat;
    cv::Mat* results;
    bool rgb;
    bool resultsSet;
    bool abandoned;
    PyObject* params;

    KogMatWithResults() = delete;
    KogMatWithResults(KogMatWithResults& other) = delete;

    inline KogMatWithResults(cv::Mat* pmat, bool prgb, bool ownsmat, PyObject* pparams) :

          mat(ownsmat ? pmat : (pmat == nullptr ? nullptr : new cv::Mat(*pmat))),
          results(nullptr), rgb(prgb), resultsSet(false), abandoned(false), params(nullptr), refCnt(0) {

      log(TRACE, "Constructing KMResult(%p)", (void*)this);
      if (pparams)
        Py_INCREF(pparams);
      params = pparams;
      increment();
    }

    inline void setResult(cv::Mat* presult, bool ownsmat) {
      if (results)
        delete results;
      if (!presult)
        results = nullptr;
      else
        results = ownsmat ? presult : new cv::Mat(*presult);
      resultsSet = true;
    }

    inline void free() {
      if (mat) {
        delete mat;
        mat = nullptr;
      }
      if (results) {
        delete results;
        results = nullptr;
      }
      resultsSet = false;
      if (params)
        Py_DECREF(params);
    }

  private:
    inline ~KogMatWithResults() {
      log(TRACE, "Destructing KMResult(%p)", (void*)this);
      free();
    }

    std::atomic_int32_t refCnt;
    std::mutex mutex;

  public:
    inline void decrement() {
      std::lock_guard<std::mutex> lck(mutex);
      int32_t val = (--refCnt);
      log(TRACE,"Decrementing KMResult(%p) to %d", (void*)this, (int)val);
      if (val <= 0)
        delete this;
    }

    inline void increment() {
      std::lock_guard<std::mutex> lck(mutex);
      int32_t val = (++refCnt);
      log(TRACE,"Incrementing KMResult(%p) to %d", (void*)this, (int)val);
    }
  };

  enum ConvertMode {
    SHALLOW_COPY,  /* note, this does not transfer ownership so the source will need to outlive the destination */
    DEEP_COPY,
    MOVE           /* note, the transfer of ownership of the underlying data means the source should not be */
                   /*     referenced after a convert is done in this mode. */
  };

  class ImageSource {
    KogMatWithResults* ondeck;
    std::mutex ondeckMutex;
    bool eos;
  public:
    ImageSource();

    inline ~ImageSource() {
      log(DEBUG, "Deleting ImageSource");
      KogMatWithResults* toDelete;
      {
        std::lock_guard<std::mutex> lck(ondeckMutex);
        toDelete = ondeck;
        ondeck = nullptr;
      }
      if (toDelete)
        toDelete->decrement();
    }

    void send(KogMatWithResults* mat);

    inline KogMatWithResults* peek() {
      std::lock_guard<std::mutex> lck(ondeckMutex);
      return ondeck;
    }

    static PyObject* convertMatToNumPyArray(cv::Mat* toConvert, ConvertMode converMode, int* statusCode, bool fromPython);
    static cv::Mat* convertNumPyArrayToMat(PyObject* npArray, ConvertMode converMode, int* statusCode, bool fromPython);
    static bool isNumpyArray(PyObject* npArray, int32_t* status);

    // it will increment the count. needs to be decremented.
    KogMatWithResults* next();
  };
}
}
