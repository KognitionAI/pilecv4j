#include <Python.h>
#include <mutex>

#include "PythonEnvironment.h"
#include "ImageSource.h"
#include "KogSystem.h"

using namespace pilecv4j;

//extern "C" {
//  // Generated from SWIG
//  PyObject* PyInit__kognition();
//}

static void initializePyType(PyTypeObject* type_object)
{
  static PyTypeObject py_type_object_header = { PyObject_HEAD_INIT(NULL) 0};
  int size = (long*)&(py_type_object_header.tp_name) - (long*)&py_type_object_header;

  memset(type_object, 0, sizeof(PyTypeObject));
  memcpy(type_object, &py_type_object_header, size);
}

//-----------------------------------------------------------
// KogMatWithResults python
//-----------------------------------------------------------
typedef struct {
  PyObject_HEAD
  KogMatWithResults* kmat;
  PyObject* npArray;
} PKogMatWithResults;

static PyTypeObject PKogMatWithResults_Type;

//-----------------------------------------------------------
// KogMatWithResults methods
//-----------------------------------------------------------

static PyObject* KogMatWithResults_get(PKogMatWithResults* self, PyObject* args)
{
  return self->npArray;
}

static PyObject* KogMatWithResults_free(PKogMatWithResults* self, PyObject* args)
{
  self->kmat->free();
  if (self->npArray) {
    Py_DECREF(self->npArray);
    self->npArray = nullptr;
  }
  Py_RETURN_NONE;
}

static PyObject* KogMatWithResults_isRgb(PKogMatWithResults* self, PyObject* args)
{
  if (self->kmat->rgb)
    Py_RETURN_TRUE;
  else
    Py_RETURN_FALSE;
}

static PyObject* KogMatWithResults_setResult(PKogMatWithResults* self, PyObject* args)
{
  log(DEBUG, "setResult called from python");

  PyObject* npArrayObj;
  if (!PyArg_ParseTuple(args, "O", &npArrayObj)) {
    PyErr_SetString(PyExc_TypeError, "None passed to setResults. A NumPy array (or None) is required.");
    return NULL;
  }

  if (npArrayObj == Py_None) {
    log(TRACE, "setResult called from python passing None");
    self->kmat->setResult(nullptr,false);
    Py_RETURN_NONE;
  }

  int statusCode;
  log(TRACE, "setResult on %ld", (long)(self->kmat));
  self->kmat->setResult(ImageSource::convertNumPyArrayToMat(npArrayObj, true, &statusCode, true),true);
  if (statusCode != OK) {
    PyErr_SetString(PyExc_TypeError, getStatusMessage(statusCode));
  }
  Py_RETURN_NONE;
}

static void KogMatWithResults_Dealloc(PKogMatWithResults* self)
{
  log(TRACE, "Deleting KogMatWithResults (%ld with kmat %ld, np %ld:%d)",
      (long)self, (long)(self->kmat), (long)self->npArray, (int)(self->npArray ? self->npArray->ob_refcnt : -1) );
  if (self->npArray) {
    Py_DECREF(self->npArray);
    self->npArray = nullptr;
  }
  if (self->kmat) {
    self->kmat->decrement();
    self->kmat = nullptr;
  }
  self->ob_base.ob_type->tp_free((PyObject*)self);
}

//------------------
// KogMatWithResults info
//------------------

static PyMethodDef KogMatWithResults_methods[] = {
    {(char*)"get", (PyCFunction)KogMatWithResults_get, METH_VARARGS, NULL},
    {(char*)"isRgb", (PyCFunction)KogMatWithResults_isRgb, METH_VARARGS, NULL},
    {(char*)"free", (PyCFunction)KogMatWithResults_free, METH_VARARGS, NULL},
    {(char*)"setResult", (PyCFunction)KogMatWithResults_setResult, METH_VARARGS, NULL},
    {NULL, NULL, 0, NULL}
};

static void initKogMatWithResults_Type()
{
  initializePyType(&PKogMatWithResults_Type);
  PKogMatWithResults_Type.tp_name = (char*)KOGNITION_MODULE ".KogMatWithResults";
  PKogMatWithResults_Type.tp_basicsize = sizeof(PKogMatWithResults);
  PKogMatWithResults_Type.tp_dealloc = (destructor)KogMatWithResults_Dealloc;
  PKogMatWithResults_Type.tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE;
  PKogMatWithResults_Type.tp_methods = KogMatWithResults_methods;
  PKogMatWithResults_Type.tp_base = 0;
  PKogMatWithResults_Type.tp_new = 0;
}

//-----------------------------------------------------------
// ImageSource python
//-----------------------------------------------------------
typedef struct {
  PyObject_HEAD
  ImageSource* imageSource;
} PImageSource;

//------------------
// ImageSource methods
//------------------
static PyObject* ImageSource_steal(PImageSource* imsrc, PyObject* args)
{
  log(DEBUG, "Creating Stolen image");
  PKogMatWithResults* ret;

  KogMatWithResults* kmat;
  Py_BEGIN_ALLOW_THREADS
  kmat = imsrc->imageSource->next();
  Py_END_ALLOW_THREADS

  if (!kmat || !kmat->mat) {
    log(DEBUG, "End of stream");
    if (kmat)
      kmat->decrement();
    Py_RETURN_NONE;
  } // else

  ret = (PKogMatWithResults*)(PKogMatWithResults_Type.tp_alloc(&PKogMatWithResults_Type, 0));
  if (!ret) {
    kmat->decrement();
    return NULL;
  }
  ret->kmat = nullptr;
  ret->npArray = nullptr;

  int statusCode;
  ret->npArray = ImageSource::convertMatToNumPyArray(kmat->mat, false, false, &statusCode, true);
  if (!ret->npArray) {
    kmat->decrement();
    return NULL;
  }
  Py_INCREF(ret->npArray);

  ret->kmat = kmat;
  log(TRACE, "Returning KogMatWithResults (refcnt:%d) (%ld with kmat %ld, np %ld:%d)",
      (int)ret->ob_base.ob_refcnt, (long)ret, (long)(ret->kmat), (long)ret->npArray, (int)(ret->npArray ? ret->npArray->ob_refcnt : -1));
  return (PyObject*)ret;
}
//------------------

//------------------
// ImageSource construction and destruction
//------------------
static void ImageSource_Dealloc(PImageSource* self)
{
  // we do not delete the image source from the python side.
  log(TRACE, "Dealloc ImageSource");
  //delete self->imageSource;
  self->ob_base.ob_type->tp_free((PyObject*)self);
}
//------------------

//------------------
// ImageSource type info
//------------------

static PyMethodDef ImageSource_methods[] = {
    {(char*)"next", (PyCFunction)ImageSource_steal, METH_VARARGS, NULL},
    {NULL, NULL, 0, NULL}
};

static PyTypeObject PImageSource_Type;

static void initImageSource_Type()
{
  initializePyType(&PImageSource_Type);
  PImageSource_Type.tp_name = (char*)KOGNITION_MODULE".ImageSource";
  PImageSource_Type.tp_basicsize = sizeof(PImageSource);
  PImageSource_Type.tp_dealloc = (destructor)ImageSource_Dealloc;
  PImageSource_Type.tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE;
  PImageSource_Type.tp_methods = ImageSource_methods;
  PImageSource_Type.tp_base = 0;
  PImageSource_Type.tp_new = 0;
}

//-----------------------------------------------------------
// KogSystem python
//-----------------------------------------------------------
typedef struct {
  PyObject_HEAD
  KogSystem* kogSys;
} PKogSystem;

//------------------
// KogSystem methods
//------------------
static PyObject* KogSystem_getImageSource(PKogSystem* pt, PyObject* args)
{
  PImageSource* ret;
  ImageSource* is;

  Py_BEGIN_ALLOW_THREADS
  log(DEBUG, "Getting ImageSource from KogSystem");
  is = pt->kogSys->getImageSource();
  Py_END_ALLOW_THREADS

  if (!is) {
    log(DEBUG, "No Image Source");
    Py_RETURN_NONE;
  } // else

  ret = (PImageSource*)(PImageSource_Type.tp_alloc(&PImageSource_Type, 0));
  if (!ret)
    return NULL;
  ret->imageSource = is;
  log(TRACE, "Returning ImageSource (refcnt:%d) (%ld)", (int)ret->ob_base.ob_refcnt, (long)ret);
  return (PyObject*)ret;
}

static PyObject* KogSystem_modelLabels(PKogSystem* pt, PyObject* args)
{
  log(DEBUG, "setting model labels");

  PyObject* list = NULL;
  if (!args || !PyArg_ParseTuple(args, "O", &list) || !list) {
    PyErr_SetString(PyExc_TypeError, "Invalid parameter.");
    return NULL;
  }

  if (!PyList_Check(list)) {
    PyErr_SetString(PyExc_TypeError, "Invalid parameter. Must be a list of strings");
    Py_DECREF(list);
    return NULL;
  }

  Py_ssize_t sz = PyList_Size(list);
  const char** nlist = KogSystem::emptyModelLabels(sz);

  for (int i = 0; i < sz; i++) {
    PyObject* cur = PyList_GetItem(list, i);
    if (!PyUnicode_Check(cur))
      goto error;

    const char* utf8 = PyUnicode_AsUTF8AndSize(cur, NULL);
    if (!utf8)
      goto error;
    nlist[i] = strdup(utf8);
  }

  pt->kogSys->setModelLabels(nlist, sz);

  Py_DECREF(list);
  Py_RETURN_NONE;

  error:
  Py_DECREF(list);
  KogSystem::freeModelLabels(nlist, sz);
  PyErr_SetString(PyExc_TypeError, "Invalid parameter. One of the list elements was not a string");
  return NULL;

}
//------------------

//------------------
// KogSystem construction and destruction
//------------------
static void KogSystem_Dealloc(PKogSystem* self)
{
  // we do not delete the image source from the python side.
  log(DEBUG, "Deallocating KogSystem");
  self->ob_base.ob_type->tp_free((PyObject*)self);
}
//------------------

//------------------
// ImageSource type info
//------------------

static PyMethodDef KogSystem_methods[] = {
    {(char*)"getImageSource", (PyCFunction)KogSystem_getImageSource, METH_VARARGS, NULL},
    {(char*)"modelLabels", (PyCFunction)KogSystem_modelLabels, METH_VARARGS, NULL},
    {NULL, NULL, 0, NULL}
};

static PyTypeObject PKogSystem_Type;

static void initKogSystem_Type()
{
  initializePyType(&PKogSystem_Type);
  PKogSystem_Type.tp_name = (char*)KOGNITION_MODULE".KogSystem";
  PKogSystem_Type.tp_basicsize = sizeof(PKogSystem);
  PKogSystem_Type.tp_dealloc = (destructor)KogSystem_Dealloc;
  PKogSystem_Type.tp_flags = Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE;
  PKogSystem_Type.tp_methods = KogSystem_methods;
  PKogSystem_Type.tp_base = 0;
  PKogSystem_Type.tp_new = 0;
}

PyObject* convert(KogSystem* pt) {
  PKogSystem* ret = (PKogSystem*)(PKogSystem_Type.tp_alloc(&PKogSystem_Type, 0));
  if (!ret) {
    return NULL;
  }

  ret->kogSys = pt;
  return (PyObject*)ret;
}


//------------------
// kognition module info
//------------------

static PyMethodDef kognition_methods[] = {
    {NULL, NULL, 0, NULL}
};

static struct PyModuleDef createModule
{
    PyModuleDef_HEAD_INIT,
    KOGNITION_MODULE,
    "",
    -1,
    kognition_methods
};

PyMODINIT_FUNC PyInit_kognition() {

  log(INFO, "Initializing " KOGNITION_MODULE " module.");
  initKogMatWithResults_Type();
  initImageSource_Type();
  initKogSystem_Type();

  if (PyType_Ready(&PKogMatWithResults_Type) < 0)
    return NULL;
  if (PyType_Ready(&PImageSource_Type) < 0)
    return NULL;
  if (PyType_Ready(&PKogSystem_Type) < 0)
    return NULL;

  Py_INCREF(&PKogMatWithResults_Type);
  Py_INCREF(&PImageSource_Type);
  Py_INCREF(&PKogSystem_Type);

  PyObject* pModule = PyModule_Create(&createModule); // PyInit__kognition(); <- SWIG

  if (pModule == NULL) {
    log(ERROR,"Failed to create module");
    return NULL;
  }

  PyModule_AddObject(pModule, (char*)"KogMatWithResults", (PyObject*)&PKogMatWithResults_Type);
  PyModule_AddObject(pModule, (char*)"ImageSource", (PyObject*)&PImageSource_Type);
  PyModule_AddObject(pModule, (char*)"KogSystem", (PyObject*)&PKogSystem_Type);

  log(TRACE, "Loaded module %s", PyModule_GetName(pModule));

  return pModule;
}

int initModule_kognition()
{
  int result = PyImport_AppendInittab(KOGNITION_MODULE, PyInit_kognition);
  if (result < 0)
    log(ERROR,"Failed to install embedded module");
  return result;
}

