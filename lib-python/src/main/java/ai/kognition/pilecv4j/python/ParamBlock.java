package ai.kognition.pilecv4j.python;

import static ai.kognition.pilecv4j.python.PythonHandle.throwIfNecessary;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import org.apache.commons.lang3.mutable.MutableBoolean;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.python.internal.PythonAPI;

public class ParamBlock {

    private final List<LongConsumer> dictCreator = new ArrayList<>(50);
    private final List<LongConsumer> tupleCreator = new ArrayList<>(50);

    public ParamBlock arg(final String kwd, final String val) {
        dictCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_dict_putString(l2, kwd, val)));
        return this;
    }

    public ParamBlock arg(final String kwd, final boolean val) {
        dictCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_dict_putBoolean(l2, kwd, val ? 1 : 0)));
        return this;
    }

    public ParamBlock arg(final String kwd, final PythonHandle val) {
        dictCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_dict_putKogSys(l2, kwd, val.nativeObj)));
        return this;
    }

    public ParamBlock arg(final String kwd, final long val) {
        dictCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_dict_putInt(l2, kwd, val)));
        return this;
    }

    public ParamBlock arg(final String kwd, final double val) {
        dictCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_dict_putFloat(l2, kwd, val)));
        return this;
    }

    public ParamBlock arg(final String val) {
        final int index = tupleCreator.size();
        tupleCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_tuple_putString(l2, index, val)));
        return this;
    }

    public ParamBlock arg(final boolean val) {
        final int index = tupleCreator.size();
        tupleCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_tuple_putBoolean(l2, index, val ? 1 : 0)));
        return this;
    }

    public ParamBlock arg(final PythonHandle val) {
        final int index = tupleCreator.size();
        tupleCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_tuple_putKogSys(l2, index, val.nativeObj)));
        return this;
    }

    public ParamBlock arg(final long val) {
        final int index = tupleCreator.size();
        tupleCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_tuple_putInt(l2, index, val)));
        return this;
    }

    public ParamBlock arg(final double val) {
        final int index = tupleCreator.size();
        tupleCreator.add(l2 -> throwIfNecessary(PythonAPI.pilecv4j_python_tuple_putFloat(l2, index, val)));
        return this;
    }

    static record Tuple(long tupleRef) implements QuietCloseable {
        @Override
        public void close() {
            PythonAPI.pilecv4j_python_tuple_destroy(tupleRef);
        }
    }

    static record Dict(long dictRef) implements QuietCloseable {
        @Override
        public void close() {
            PythonAPI.pilecv4j_python_dict_destroy(dictRef);
        }
    }

    Tuple buildArgs() {
        final long tupleRef = PythonAPI.pilecv4j_python_tuple_create(tupleCreator.size());
        if(tupleRef == 0)
            throw new IllegalStateException("Failed to create a python PyTuple of size " + tupleCreator.size());

        final MutableBoolean doClose = new MutableBoolean(true);
        try(QuietCloseable q = () -> {
            if(doClose.booleanValue())
                PythonAPI.pilecv4j_python_tuple_destroy(tupleRef);
        };) {
            tupleCreator.forEach(c -> c.accept(tupleRef));
            doClose.setFalse();
        }

        return new Tuple(tupleRef);
    }

    Dict buildKeywordArgs() {
        final long dictRef = PythonAPI.pilecv4j_python_dict_create();
        if(dictRef == 0)
            throw new IllegalStateException("Failed to create a python PyDict");

        final MutableBoolean doClose = new MutableBoolean(true);
        try(QuietCloseable q = () -> {
            if(doClose.booleanValue())
                PythonAPI.pilecv4j_python_dict_destroy(dictRef);
        };) {
            dictCreator.forEach(c -> c.accept(dictRef));
            doClose.setFalse();
        }

        return new Dict(dictRef);
    }

    public static ParamBlock builder() {
        return new ParamBlock();
    }
}
