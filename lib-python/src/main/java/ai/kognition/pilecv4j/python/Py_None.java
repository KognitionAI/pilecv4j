package ai.kognition.pilecv4j.python;

import ai.kognition.pilecv4j.python.internal.PythonAPI;

public class Py_None extends PyObject {

    Py_None() {
        super(PythonAPI.pilecv4j_python_pyObject_PyNone(1), false);
    }

}
