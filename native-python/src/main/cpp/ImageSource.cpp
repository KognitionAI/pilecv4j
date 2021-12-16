#include <chrono>
#include <thread>
#define NPY_NO_DEPRECATED_API NPY_1_7_API_VERSION
#include <numpy/arrayobject.h>

#include "ImageSource.h"
#include "PythonEnvironment.h"

#define SPINS 1

// CV_8U   0
// CV_8S   1
// CV_16U  2
// CV_16S  3
// CV_32S  4
// CV_32F  5
// CV_64F  6
// CV_16F  7
const char* lookupCvTypeNames[] = {
     "CV_8U",
     "CV_8S",
     "CV_16U",
     "CV_16S",
     "CV_32S",
     "CV_32F",
     "CV_64F",
     "CV_16F"
};

static NPY_TYPES lookupFromCvToNp[] = { NPY_UINT8, NPY_INT8, NPY_UINT16, NPY_INT16, NPY_INT32, NPY_FLOAT32, NPY_FLOAT64, NPY_FLOAT16 };
#define LOOKUP_CV_TO_NP_SIZE 8

static int* initLookupFromNpToCv() {
  int* ret = new int[NPY_NTYPES_ABI_COMPATIBLE];
  for (int nptype = 0; nptype < NPY_NTYPES_ABI_COMPATIBLE; nptype++) {
    // find the corresponding index in lookupFromCvToNp for the given nptype.
    int foundIndex = -1;
    for (int index = 0; index < LOOKUP_CV_TO_NP_SIZE; index++) {
      if (lookupFromCvToNp[index] == nptype) {
        foundIndex = index;
        break;
      }
    }
    ret[nptype] = foundIndex;
  }
  return ret;
}

static int* lookupFromNpToCv = initLookupFromNpToCv();

const char* lookupCvTypeName(int cvType) {
  static const char* unknown = "UNKNOWN";
  if (cvType >= LOOKUP_CV_TO_NP_SIZE || cvType < 0)
    return unknown;
  else
    return lookupCvTypeNames[cvType];
}

namespace pilecv4j {

  ImageSource::ImageSource() : ondeckx(nullptr), eos(false) {
    log(TRACE,"instantiating ImageSource %ld", (uint64_t)this );
  }

  // only good here
  // it will increment the count. needs to be decremented.
  KogMatWithResults* ImageSource::next() {
    for (int spinCount = 0; spinCount < SPINS; spinCount++) {
      {
        std::lock_guard<std::mutex> lck(ondeckMutex);
        KogMatWithResults* ret = ondeckx;
        ondeckx = nullptr;
        if (ret) {
          log(TRACE,"retrieving KogMatWithResults %ld, count:0", (long)ret );
          return ret;
        } else if (eos) {
          log(TRACE,"EOS(1) on ImageSource detected");
          return nullptr;
        }
      }
    }
    // if we got here we need to relent on the spinning
    KogMatWithResults* ret = nullptr;
    uint64_t count = 0;
    while (!ret) {
      count++;
      using std::chrono::operator""us;
      std::this_thread::sleep_until(std::chrono::steady_clock::now() + 1us);
      {
        std::lock_guard<std::mutex> lck(ondeckMutex);
        ret = ondeckx;
        ondeckx = nullptr;
        if (!ret && eos) {
          log(TRACE,"EOS(2) on ImageSource detected %ld", (long)count);
          return nullptr;
        }
      }
    }
    log(TRACE,"retrieving Mat %ld count:%ld", (long)ret, (long)count );
    return ret;
  }

  void ImageSource::send(KogMatWithResults* mat) {
    std::lock_guard<std::mutex> lck(ondeckMutex);
    KogMatWithResults* prev = nullptr;
    if (!mat) {
      // this is an EOS indicator
      eos = true;
      prev = ondeckx;
      ondeckx = nullptr;
    } else {
      log(TRACE,"sending KogMatWithResults %ld", (uint64_t)mat);
      prev = ondeckx;
      mat->increment();
      ondeckx = mat;
    }
    if (prev) {
      log(INFO,"decrementing KogMatWithResults %ld on abandoned result", (uint64_t)prev );
      prev->abandoned = true;
      prev->decrement();
    }
  }

  PyObject* ImageSource::convertMatToNumPyArray(cv::Mat* mat, bool ownsMatPassed, bool deepcopy, int* statusCode, bool fromPython) {
    *statusCode = OK;
    if (!PythonEnvironment::instance()->numpyImported) {
      import_array();
      PythonEnvironment::instance()->numpyImported = true;
    }

    std::unique_ptr<cv::Mat> ig(ownsMatPassed ? mat : nullptr);

    // This should indicate the end of available images.
    if (mat == nullptr) {
      log(INFO,"No more images available");
      Py_RETURN_NONE;
    }

    if (!mat->isContinuous()) {
      log(ERROR,"Can't pass a Mat to python that isn't continuous.");
      *statusCode = ILLEGAL_ARGUMENT;
      if (fromPython)
        PyErr_SetString(PyExc_RuntimeError, "Can't pass a Mat to python that isn't continuous.");
      return NULL;
    }

    npy_intp dimensions[3] = {mat->rows, mat->cols, mat->channels()};
    int nbytes = mat->rows * mat->cols * mat->channels();
    uchar* bytes = deepcopy ? (uchar*)malloc(nbytes) : mat->data;
    if (deepcopy)
      std::memcpy(bytes,mat->data,nbytes);

    PyObject* ret = PyArray_SimpleNewFromData(mat->dims + 1, dimensions, lookupFromCvToNp[mat->depth()], bytes);
    if (deepcopy)
      PyArray_ENABLEFLAGS((PyArrayObject*) ret, NPY_ARRAY_OWNDATA);
    return ret;
  }

  cv::Mat* ImageSource::convertNumPyArrayToMat(PyObject* npArrayObj, bool deepcopy, int* statusCode, bool fromPython) {
    *statusCode = OK;

    if (!PythonEnvironment::instance()->numpyImported) {
      import_array();
      PythonEnvironment::instance()->numpyImported = true;
    }

    log(TRACE,"ImageSource::convertNumPyArrayToMat (%ld,%s).",(long)npArrayObj,deepcopy ? "True" : "False");
    if (!npArrayObj || npArrayObj == Py_None) {
      log(WARN,"Null NumPy array passed to convert to Mat. Returning nullptr");
      *statusCode = ILLEGAL_ARGUMENT;
      if (fromPython)
        PyErr_SetString(PyExc_TypeError, "Null NumPy array passed to convert to Mat. Returning nullptr");
      return NULL;
    }

    log(TRACE,"ImageSource::convertNumPyArrayToMat checking that passed python object is an np array.");
    if( !PyArray_Check(npArrayObj) ) {
      log(ERROR,"Non NumPy array passed to convert to Mat.");
      *statusCode = ILLEGAL_ARGUMENT;
      if (fromPython)
        PyErr_SetString(PyExc_TypeError, "Non NumPy array passed to convert to Mat.");
      return NULL;
    }

    PyArrayObject* npArray = (PyArrayObject*)npArrayObj;

    int typenum = PyArray_TYPE(npArray);
    int type = (typenum < 0 || typenum >= NPY_NTYPES_ABI_COMPATIBLE) ? -1 : lookupFromNpToCv[typenum];
    if (type < 0) {
      log(ERROR,"NumPy array passed has unrecognized NP type: %d", typenum);
      *statusCode = ILLEGAL_ARGUMENT;
      if (fromPython)
        PyErr_SetString(PyExc_TypeError, "NumPy array passed has unrecognized NP type.");
      return NULL;
    }

    if (isEnabled(TRACE))
      log(TRACE,"ImageSource::convertNumPyArrayToMat cv type %s(%d)", lookupCvTypeName(type), type);

    int ndims = PyArray_NDIM(npArray);
    log(TRACE,"ImageSource::convertNumPyArrayToMat number of dims %d", ndims);

    int size[ndims+1];
    size_t step[ndims+1];
    size_t elemsize = CV_ELEM_SIZE1(type);
    const npy_intp* _sizes = PyArray_DIMS(npArray);
    const npy_intp* _strides = PyArray_STRIDES(npArray);

    for(int i = 0; i < ndims; i++) {
      size[i] = (int)_sizes[i];
      step[i] = (size_t)_strides[i];
    }

    if( ndims == 0 || step[ndims-1] > elemsize ) {
      size[ndims] = 1;
      step[ndims] = elemsize;
      ndims++;
    }

    if( ndims == 3 && size[2] <= CV_CN_MAX && step[1] == elemsize*size[2] )   {
      ndims--;
      type |= CV_MAKETYPE(0, size[2]);
    }

    // This is Zero Copy
    cv::Mat* tmp = new cv::Mat(ndims, size, type, PyArray_DATA(npArray), step);
    if (deepcopy) {
      cv::Mat* ret = new cv::Mat();
      *ret = tmp->clone();
      delete tmp;
      return ret;
    } else {
      return tmp;
    }
  }
}