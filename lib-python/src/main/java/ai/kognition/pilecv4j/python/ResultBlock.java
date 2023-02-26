package ai.kognition.pilecv4j.python;

import java.util.List;

import com.sun.jna.Pointer;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.python.internal.PythonAPI;

public class ResultBlock implements QuietCloseable {
    private final Pointer results;
    private final int resultSize;
    private Object parsed = null;

    ResultBlock(final Pointer results, final int resultSize) {
        this.results = results;
        this.resultSize = resultSize;
    }

    public Object parse() {
        parseIfNecessary();
        return parsed;
    }

    public long longValue() {
        parseIfNecessary();
        return ((Number)parsed).longValue();
    }

    public int intValue() {
        parseIfNecessary();
        return ((Number)parsed).intValue();
    }

    public short shortValue() {
        parseIfNecessary();
        return ((Number)parsed).shortValue();
    }

    public byte byteValue() {
        parseIfNecessary();
        return ((Number)parsed).byteValue();
    }

    public float floatValue() {
        parseIfNecessary();
        return ((Number)parsed).floatValue();
    }

    public double doubleValue() {
        parseIfNecessary();
        return ((Number)parsed).doubleValue();
    }

    @Override
    public void close() {
        PythonHandle.throwIfNecessary(PythonAPI.pilecv4j_python_freeFunctionResults(results));
    }

    public Object doparse() {
        final var ret = PythonHandle.parseResult(results.getByteBuffer(0, resultSize));
        return ret;
    }

    public CvMat asMat() {
        parseIfNecessary();
        return CvMat.shallowCopy(((CvMat)parsed));
    }

    public PyObject asPyObject() {
        parseIfNecessary();
        return parsed == null ? null : ((PyObject)parsed).shallowCopy();
    }

    public List<?> asList() {
        parseIfNecessary();
        return parsed == null ? null : ((List<?>)parsed);
    }

    private void parseIfNecessary() {
        if(parsed == null)
            parsed = doparse();
    }

}
