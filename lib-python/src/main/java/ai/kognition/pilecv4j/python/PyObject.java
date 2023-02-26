package ai.kognition.pilecv4j.python;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.python.internal.PythonAPI;

public class PyObject implements QuietCloseable {
    final long nativeRef;
    private boolean closed = false;
    private final boolean unmanaged;

    PyObject(final long nativeRef, final boolean unmanaged) {
        if(nativeRef == 0)
            throw new IllegalArgumentException("Null PyObject");
        this.unmanaged = unmanaged;
        if(!unmanaged)
            PythonAPI.pilecv4j_python_pyObject_incref(nativeRef);
        this.nativeRef = nativeRef;
    }

    @Override
    public void close() {
        if(!closed && !unmanaged)
            PythonAPI.pilecv4j_python_pyObject_decref(nativeRef);
        closed = true;
    }

    public PyObject shallowCopy() {
        return new PyObject(nativeRef, false);
    }
}
