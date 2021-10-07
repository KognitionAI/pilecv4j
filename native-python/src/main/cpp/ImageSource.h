#pragma once
#include <mutex>

#include <opencv2/core/mat.hpp>
#include "PythonEnvironment.h"
#include <atomic>
#include <Python.h>

namespace pilecv4j {

  struct KogMatWithResults {
    cv::Mat* mat;
    cv::Mat* results;
    bool rgb;
    bool resultsSet;
    bool abandoned;

    KogMatWithResults() = delete;
    KogMatWithResults(KogMatWithResults& other) = delete;

    inline KogMatWithResults(cv::Mat* pmat, bool prgb, bool ownsmat) :
      mat(ownsmat ? pmat : (pmat == nullptr ? nullptr : new cv::Mat(*pmat))),
      results(nullptr),
      rgb(prgb),
      resultsSet(false),
      abandoned(false),
      refCnt(0) {
      log(TRACE,"Constructing KMResult(%ld)", (long)this);
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
    }

  private:
    inline ~KogMatWithResults() {
      log(TRACE,"Destructing KMResult(%ld)", (long)this);
      free();
    }

    std::atomic_int32_t refCnt;
    std::mutex mutex;

  public:
    inline void decrement() {
      std::lock_guard<std::mutex> lck(mutex);
      int32_t val = (--refCnt);
      log(TRACE,"Decrementing KMResult(%ld) to %d", (long)this, (int)val);
      if (val <= 0)
        delete this;
    }

    inline void increment() {
      std::lock_guard<std::mutex> lck(mutex);
      int32_t val = (++refCnt);
      log(TRACE,"Incrementing KMResult(%ld) to %d", (long)this, (int)val);
    }
  };

  class ImageSource {
    KogMatWithResults* ondeckx;
    std::mutex ondeckMutex;
    bool eos;
  public:
    ImageSource();

    inline ~ImageSource() {
      log(DEBUG, "Deleting ImageSource");
      KogMatWithResults* toDelete;
      {
        std::lock_guard<std::mutex> lck(ondeckMutex);
        toDelete = ondeckx;
        ondeckx = nullptr;
      }
      if (toDelete)
        toDelete->decrement();
    }

    void send(KogMatWithResults* mat);

    inline KogMatWithResults* peek() {
      std::lock_guard<std::mutex> lck(ondeckMutex);
      return ondeckx;
    }

    static PyObject* convertMatToNumPyArray(cv::Mat* toConvert, bool ownsMatPassed, bool deepcopy, int* statusCode, bool fromPython);
    static cv::Mat* convertNumPyArrayToMat(PyObject* npArray, bool deepcopy, int* statusCode, bool fromPython);
  public:
    // it will increment the count. needs to be decremented.
    KogMatWithResults* next();
  };
}
