/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.kognition.pilecv4j.python;

import static ai.kognition.pilecv4j.python.internal.PythonAPI.LOG_LEVEL_DEBUG;
import static ai.kognition.pilecv4j.python.internal.PythonAPI.LOG_LEVEL_ERROR;
import static ai.kognition.pilecv4j.python.internal.PythonAPI.LOG_LEVEL_FATAL;
import static ai.kognition.pilecv4j.python.internal.PythonAPI.LOG_LEVEL_INFO;
import static ai.kognition.pilecv4j.python.internal.PythonAPI.LOG_LEVEL_TRACE;
import static ai.kognition.pilecv4j.python.internal.PythonAPI.LOG_LEVEL_WARN;
import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.python.ParamBlock.Dict;
import ai.kognition.pilecv4j.python.ParamBlock.Tuple;
import ai.kognition.pilecv4j.python.internal.PythonAPI;
import ai.kognition.pilecv4j.python.internal.PythonAPI.get_image_source;

/**
 * <p>
 * This object can be used to call functions in Python. It can be used to simply
 * call a function or it can be used to set up a message exchange between a
 * a function running in Python in one thread, and a function running in Java
 * in another thread. The more straightforward way is to just call a function.
 * </p>
 * <p>
 * As an example of how to simply call a function. First you create a {@link PythonHandle}.
 *
 * <pre>
 * <code>
 * try (final PythonHandle python = new PythonHandle();) {
 * </code>
 * </pre>
 * </p>
 *
 * <p>
 * You can add paths for modules. This is typical since you're probably
 * running a script that's not already on the PYTHONPATH. For example.
 * </p>
 *
 * <pre>
 * <code>
 * python.addModulePath("/path/to/directory/with/python_files");
 * </code>
 * </pre>
 * </p>
 *
 * <p>
 * You can also have the PythonHandle expand python modules that are in
 * jar files on the classpath. In the following example, there's a directory
 * in the jar file called "python" that has scripts in it.
 * </p>
 *
 * <pre>
 * <code>
 * python.unpackAndAddModule("classpath:///python");
 * </code>
 * </pre>
 * </p>
 *
 * <p>
 * Finally, you can do this in one step while creating the python handle.
 * </p>
 *
 * <pre>
 * <code>
 * try (PythonHandle python = PythonHandle.initModule("classpath:///python");) {
 * </code>
 * </pre>
 * </p>
 *
 * There are two different modes that communication with Python can operate. You
 * can simply synchronously call a function in a Python *.py file (<em>SYNCHRONOUS</em>
 * mode), or you can start a Python function in a separate thread and set up
 * a hand-off between Java and Python that allows for passing images and retrieving
 * results (ASYNCHRONOUS mode).
 */
public class PythonHandle implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonHandle.class);
    private static final Object pythonExpandedLock = new Object();
    private static Map<String, File> pythonIsExpanded = new HashMap<>();

    private static final int PyResultNONE = 0;
    private static final int PyResultLONG = 1;
    private static final int PyResultFLOAT = 2;
    private static final int PyResultSTRING = 3;
    private static final int PyResultMAT = 4;
    private static final int PyResultPyObject = 5;
    private static final int PyResultLIST = 6;

    static {
        // find the level
        final int logLevelSet;
        if(LOGGER.isTraceEnabled())
            logLevelSet = LOG_LEVEL_TRACE;
        else if(LOGGER.isDebugEnabled())
            logLevelSet = LOG_LEVEL_DEBUG;
        else if(LOGGER.isInfoEnabled())
            logLevelSet = LOG_LEVEL_INFO;
        else if(LOGGER.isWarnEnabled())
            logLevelSet = LOG_LEVEL_WARN;
        else if(LOGGER.isErrorEnabled())
            logLevelSet = LOG_LEVEL_ERROR;
        else
            logLevelSet = LOG_LEVEL_FATAL;

        throwIfNecessary(PythonAPI.pilecv4j_python_setLogLevel(logLevelSet));
        throwIfNecessary(PythonAPI.pilecv4j_python_initPython());
    }

    public ImageSource imageSource = null;

    long nativeObj = 0L;

    private String currentModule;
    private String currentFunction;

    private final get_image_source callback = new get_image_source() {
        @Override
        public long image_source(final long ptRef) {
            synchronized(PythonHandle.this) {
                if(imageSource == null) imageSource = new ImageSource(PythonAPI.pilecv4j_python_imageSource_create(ptRef));
                return imageSource.imageSourceRef;
            }
        }
    };

    /**
     * Create a {@link PythonHandle}
     *
     * @throws PythonException if the underlying Python environment couldn't be instantiated
     */
    public PythonHandle() throws PythonException {
        nativeObj = PythonAPI.pilecv4j_python_kogSys_create(callback);
        if(nativeObj == 0L)
            throw new PythonException("Failed to instantiate native PyTorch instance.");
    }

    /**
     * Run a Python function. Once you have the modules set up (see {@link PythonHandle})
     * you can invoke a function in from a *.py file. For example, if you have a python file
     * called "python_script.py" that has a function in it called "def func(...):" then
     * you can invoke it with parameters as follows:
     *
     * <pre>
     * <code>
     * try (ResultBlock results = python.runPythonFunction("python_script", "fumc",
     *               ParamBlock.builder()
     *                 .arg(arg1)
     *                 .arg(arg2)
     *                 .arg("keyword",kwdArg)
     *                 ...);) {
     * </code>
     * </pre>
     *
     * <p>
     * You can pass parameters of the following types:
     * </p>
     * <ul>
     * <li>String</li>
     * <li>An numeric type.</li>
     * <li>A {@link CvMat}</li>
     * <li>The PythonHandle itself. This is primarily used to set up a communication channel between
     * a running Python script and the Java side.</li>
     * <li>A list of any of these (including another list)</li>
     * </ul>
     *
     * <p>
     * The ResultBlock will hold the return from the function. If the function has no return
     * value then the ResultBlock will be null. You can retrieve any of the following types:
     * </p>
     *
     * <ul>
     * <li>String</li>
     * <li>An numeric type.</li>
     * <li>A {@link CvMat} - you will get a shallow copy.</li>
     * <li>A PyObject. This will be an opaque handle to an underlying Python return object.
     * It can passed back into another script</li>
     * <li>A list of any of these (including another list)</li>
     * </ul>
     *
     */
    public ResultBlock runPythonFunction(final String module, final String function, final ParamBlock params) {
        try(Tuple args = params.buildArgs();
            Dict kwds = params.buildKeywordArgs();) {

            final PointerByReference result = new PointerByReference();
            final IntByReference resultSizeByRef = new IntByReference();

            throwIfNecessary(PythonAPI.pilecv4j_python_runPythonFunction(module, function, args.tupleRef(),
                kwds.dictRef(), result, resultSizeByRef));

            final int resultSize = resultSizeByRef.getValue();

            final Pointer p = result.getValue();
            if(resultSize == 0 || p.equals(Pointer.NULL))
                return null;

            return new ResultBlock(p, resultSize);
        }
    }

    /**
     * When running a Python script asynchronously, this object will represent the state
     * of the Python script.
     */
    public static class PythonRunningState {
        public final AtomicBoolean isRunning = new AtomicBoolean(false);
        public final AtomicReference<RuntimeException> failed = new AtomicReference<>();
        public Thread thread = null;

        private final PythonHandle system;

        private PythonRunningState(final PythonHandle system) {
            this.system = system;
        }

        public boolean hasFailed() {
            return failed.get() != null;
        }

        public boolean sourceIsInitialized() {
            return system.imageSource != null;
        }

        public void waitUntilSourceInitialized(final long timeout) {
            final long startTime = System.currentTimeMillis();
            while(!sourceIsInitialized() && (System.currentTimeMillis() - startTime) < timeout && !hasFailed())
                Thread.yield();

            if(hasFailed())
                throw new PythonException(
                    "The module \"" + system.currentModule + ".py\" using function \"" + system.currentFunction
                        + "\" failed with the following exception before it ever initialized the source:" +
                        failed.get());
            if(!sourceIsInitialized())
                throw new PythonException(
                    "The module \"" + system.currentModule + ".py\" using function \"" + system.currentFunction
                        + "\" never initialized the image source. Did you call runPythonFunction somewhere?");
        }

    }

    /**
     * When in SYNCHRONOUS mode, given the use of calling Python in PileCV4J is to call
     * a neural network, you can write the script to hand the labels (classes) back to Java.
     * This is usually done on the script side before initializing the source and on the Java
     * side this should then be called after the source is initialized
     * (see {@link PythonRunningState#waitUntilSourceInitialized(long)}) and then call this method.
     */
    public String[] retrieveModelLabels() {
        // how many labels does the model handle.
        final int numModelLabels = numModelLabels();

        // retrieve the model labels from the python side
        final String[] labels = new String[numModelLabels];
        for(int i = 0; i < numModelLabels; i++)
            labels[i] = getModelLabel(i);
        return labels;
    }

    /**
     * Run the script asynchronously. It is assumed the python function will loop and
     * communicate back with the Java side through the {@link ImageSource}. If you just want
     * to call a Python function you should use {@link PythonHandle#runPythonFunction(String, String, ParamBlock)}.
     */
    public PythonRunningState runPythonFunctionAsynch(final String module, final String function, final ParamBlock pb) {
        final var ret = new PythonRunningState(this);
        final AtomicBoolean started = new AtomicBoolean(false);
        chain(
            ret.thread = new Thread(() -> {
                ret.isRunning.set(true);
                started.set(true);
                try {
                    runPythonFunction(module, function, pb);
                } catch(final RuntimeException rte) {
                    LOGGER.error("Python function call {} (from module {}) with parameters {} failed", function, module, pb, rte);
                    rte.printStackTrace();
                    throw rte;
                } finally {
                    ret.isRunning.set(false);
                }
            }, "Python Thread"),
            t -> t.setDaemon(true),
            t -> t.start());

        while(started.get() == false)
            Thread.yield();

        return ret;
    }

    /**
     * Add a path to the Python environment where Python should search for modules (*.py files).
     */
    public void addModulePath(final String dir) {
        final String absDir = FileSystems.getDefault().getPath(dir).normalize().toAbsolutePath().toString();
        PythonAPI.pilecv4j_python_addModulePath(absDir);
    }

    /**
     * While running in ASYNCHRONOUS mode, you can send a {@link CvMat} vide the
     * image source to the running Python script. Obviously, on the Pthon side, you
     * will have needed to write the script to read from the ImageSource.
     */
    public PythonResults sendMat(final CvMat mat, final boolean isRgb, final ParamBlock params) {
        if(imageSource != null)
            return imageSource.send(mat, isRgb, params);
        throw new IllegalStateException("There's no current image source");
    }

    /**
     * While running in ASYNCHRONOUS mode, send an indication to the Python script that
     * we're finished.
     */
    public void eos() {
        sendMat(null, false, null);
    }

    /**
     * Clean up the resources. This will close the ImageSource if it's been created
     * for communication in ASYNCHRONOUS mode, and also close done the Python interpreter.
     */
    @Override
    public void close() {
        if(imageSource != null)
            imageSource.close();

        if(nativeObj != 0)
            PythonAPI.pilecv4j_python_kogSys_destroy(nativeObj);
    }

    /**
     * Create the {@link PythonHandle), unpack the Python module located at
     * {@code pythonModuleUri} (e.g. "classpath:///python"), and add the
     * unpacked module to the Python path.
     */
    public static PythonHandle initModule(final String pythonModuleUri) {
        final File pythonModulePath = unpackModule(pythonModuleUri);
        final PythonHandle ret = new PythonHandle();
        ret.addModulePath(pythonModulePath.getAbsolutePath());
        return ret;
    }

    /**
     * This is will unpack a module and add it to the path that Python searches
     * for *.py modules.
     */
    public void unpackAndAddModule(final String pythonModuleUri) {
        final File tmpDirWithPythonModule = unpackModule(pythonModuleUri);
        addModulePath(tmpDirWithPythonModule.getAbsolutePath());
    }

    /**
     * This is will unpack a module into a temp directory and return
     * to you the path where it was unpacked.
     */
    public static File unpackModule(final String pythonModuleUri) {
        synchronized(pythonExpandedLock) {
            final File ret = pythonIsExpanded.get(pythonModuleUri);
            if(ret == null) {
                try(Vfs vfs = new Vfs();) {
                    final Path path = vfs.toPath(uncheck(() -> new URI(pythonModuleUri)));
                    if(!path.exists() || !path.isDirectory())
                        throw new IllegalStateException("The python code isn't properly bundled in the jar file.");

                    final File pythonCodeDir = Files.createTempDirectory("pilecv4j-lib-python").toFile();
                    pythonCodeDir.deleteOnExit();

                    copy(path, pythonCodeDir.getAbsolutePath(), true);

                    pythonIsExpanded.put(pythonModuleUri, pythonCodeDir);
                    return pythonCodeDir;
                } catch(final IOException ioe) {
                    throw new IllegalStateException("Failed to expand python code.", ioe);
                }
            }
            return ret;
        }
    }

    /**
     * When communicating an ASYNCHRONOUS mode you send a Mat using {@link PythonHandle#sendMat(CvMat, boolean, ParamBlock)}
     * and you'll get a {@link PythonResults} back. This acts like a Java Future. The script should eventually
     * set a result Mat from the CNN operation in response to the {@code sendMat}. At that point
     * {@link PythonResults#hasResult()} will return true and {@link PythonResults#getResultMat()} will
     * return the results that were set from the Python script.
     */
    public static class PythonResults implements QuietCloseable {
        private final long nativeObj;

        PythonResults(final long nativeObj) {
            this.nativeObj = nativeObj;
        }

        /**
         * Once the Python script has set the result of an operation that was started using
         * {@link PythonHandle#sendMat(CvMat, boolean, ParamBlock)}, this will return those results. Until
         * then it will return null. You can poll for the result using {PythonResults{@link #hasResult()}.
         */
        public CvMat getResultMat() {
            if(nativeObj != 0L) {
                final long resRef = PythonAPI.pilecv4j_python_kogMatResults_getResults(nativeObj);
                if(resRef != 0L)
                    return CvMat.wrapNative(resRef);
                else
                    return null;
            }
            throw new NullPointerException("Illegal KogMatResults. Null underlying reference.");
        }

        /**
         * Clean up the underlying resources. The {@link PythonResults} should not be used
         * after they have been closed.
         */
        @Override
        public void close() {
            if(nativeObj != 0L)
                PythonAPI.pilecv4j_python_kogMatResults_destroy(nativeObj);
        }

        /**
         * Once the Python script has set the result of an operation that was started using
         * {@link PythonHandle#sendMat(CvMat, boolean, ParamBlock)}, this will return true and
         * any actual results can be retrieved using {@link PythonResults#getResultMat()}.
         */
        public boolean hasResult() {
            if(nativeObj != 0L)
                return PythonAPI.pilecv4j_python_kogMatResults_hasResult(nativeObj) == 0 ? false : true;
            throw new NullPointerException("Illegal KogMatResults. Null underlying reference.");
        }

        public boolean isAbandoned() {
            if(nativeObj != 0L)
                return PythonAPI.pilecv4j_python_kogMatResults_isAbandoned(nativeObj) == 0 ? false : true;
            throw new NullPointerException("Illegal KogMatResults. Null underlying reference.");
        }
    }

    static class ImageSource implements QuietCloseable {
        private final long imageSourceRef;

        ImageSource(final long imageSourceRef) {
            this.imageSourceRef = imageSourceRef;
        }

        public PythonResults send(final CvMat mat, final boolean isRgb) {
            return send(mat, isRgb, 0L);
        }

        public PythonResults send(final CvMat mat, final boolean isRgb, final ParamBlock params) {
            if(params == null)
                return send(mat, isRgb, 0L);
            try(Dict kwds = params.buildKeywordArgs();) {
                return send(mat, isRgb, kwds.dictRef());
            }
        }

        public long peek() {
            return PythonAPI.pilecv4j_python_imageSource_peek(imageSourceRef);
        }

        @Override
        public void close() {
            PythonAPI.pilecv4j_python_imageSource_destroy(imageSourceRef);
        }

        private PythonResults send(final CvMat mat, final boolean isRgb, final long dictRef) {
            final long result;
            if(mat != null)
                result = PythonAPI.pilecv4j_python_imageSource_send(imageSourceRef, dictRef, mat.nativeObj, (isRgb ? 1 : 0));
            else
                result = PythonAPI.pilecv4j_python_imageSource_send(imageSourceRef, dictRef, 0L, 0);
            return (result == 0L) ? null : new PythonResults(result);
        }
    }

    static Object parseResult(final ByteBuffer bb) {
        final byte type = bb.get();
        switch(type) {
            case PyResultNONE:
                return null;
            case PyResultLONG:
                return bb.getLong();
            case PyResultFLOAT:
                return bb.getDouble();
            case PyResultSTRING: {
                final int size = bb.getInt();
                final byte[] strBytes = new byte[size];
                bb.get(strBytes);
                return new String(strBytes, StandardCharsets.UTF_8);
            }
            case PyResultMAT: {
                final long nativeRef = bb.getLong();
                try(var qc = new UnmanagedMat(nativeRef);) {
                    return qc;
                }
            }
            case PyResultPyObject: {
                final long nativeRef = bb.getLong();
                final var ret = new PyObject(nativeRef, true);
                return ret;
            }
            case PyResultLIST: {
                final int size = bb.getInt();
                final List<Object> ret = new ArrayList<>(size);
                for(int i = 0; i < size; i++) {
                    ret.add(parseResult(bb));
                }
                return ret;
            }
            default:
                throw new IllegalArgumentException("Can't handle result type:" + type);
        }
    }

    static void throwIfNecessary(final int status) throws PythonException {
        if(status != 0) {
            final Pointer p = PythonAPI.pilecv4j_python_status_message(status);
            try(final QuietCloseable qc = () -> PythonAPI.pilecv4j_python_status_freeMessage(p);) {
                if(Pointer.nativeValue(p) == 0L)
                    throw new PythonException("Null status message. Status code:" + status);
                else {
                    final String message = p.getString(0);
                    throw new PythonException(message);
                }
            }
        }
    }

    private static String stripTrailingSlash(final String path) {
        if(path.endsWith("/") || path.endsWith("\\"))
            return path.substring(0, path.length() - 1);
        else
            return path;
    }

    private static class UnmanagedMat extends CvMat {
        private UnmanagedMat(final long nativeRef) {
            super(nativeRef);
        }

        // we're skipping the delete because this mat is actually
        // managed by the result block
        @Override
        protected void doNativeDelete() {}
    }

    private static String getPath(final URI uri) {
        final String pathToUse;
        if("jar".equals(uri.getScheme())) {
            final String uriStr = uri.toString();
            int indexOfEx = uriStr.lastIndexOf('!');
            if(indexOfEx < 0) {
                // just cut off from the last ':'
                indexOfEx = uriStr.lastIndexOf(':');
                if(indexOfEx < 0)
                    throw new IllegalArgumentException("Cannot interpret the jar uri: " + uriStr);
            }
            pathToUse = uriStr.substring(indexOfEx + 1);
        } else
            pathToUse = uri.getPath();
        return pathToUse;
    }

    private static void copy(final Path from, final String destDirStrX, final boolean skipThisDir) throws IOException {
        final String destDirStr = stripTrailingSlash(destDirStrX);
        final File destDir = new File(destDirStr);
        if(!destDir.exists())
            destDir.mkdirs();
        if(!destDir.isDirectory())
            throw new IOException("The destination \"" + destDir.getAbsolutePath() + "\" was expected to be a directory.");

        // if from is a direrectory, we need to act recursively.
        if(from.isDirectory()) {
            final String newDest;
            if(skipThisDir) {
                newDest = destDir.getAbsolutePath();
            } else {
                final String relativeName = new File(getPath(from.uri())).getName();
                newDest = destDir.getAbsolutePath() + "/" + relativeName;
            }
            for(final Path sp: from.list()) {
                copy(sp, newDest, false);
            }
        } else {
            final String filename = new File(getPath(from.uri())).getName();
            try(InputStream is = from.read();) {
                FileUtils.copyInputStreamToFile(is, new File(destDir, filename));
            }
        }
    }

    private String getModelLabel(final int i) {
        final Pointer ml = PythonAPI.pilecv4j_python_kogSys_modelLabel(nativeObj, i);
        if(Pointer.nativeValue(ml) == 0L)
            return null;
        else
            return ml.getString(0);
    }

    private int numModelLabels() {
        return PythonAPI.pilecv4j_python_kogSys_numModelLabels(nativeObj);
    }
}
