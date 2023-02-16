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
import static net.dempsy.util.Functional.uncheck;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Pointer;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.QuietCloseable;
import net.dempsy.vfs.Path;
import net.dempsy.vfs.Vfs;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.python.internal.PythonAPI;
import ai.kognition.pilecv4j.python.internal.PythonAPI.get_image_source;

public class Python implements QuietCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Python.class);

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
    private long nativeObj = 0L;

    private String currentModule;
    private String currentFunction;

    private final get_image_source callback = new get_image_source() {
        @Override
        public long image_source(final long ptRef) {
            synchronized(Python.this) {
                if(imageSource == null)
                    imageSource = new ImageSource(PythonAPI.pilecv4j_python_imageSource_create(ptRef));
                return imageSource.imageSourceRef;
            }
        }
    };

    public Python() throws PythonException {
        nativeObj = PythonAPI.pilecv4j_python_kogSys_create(callback);
        if(nativeObj == 0L)
            throw new PythonException("Failed to instantiate native PyTorch instance.");
    }

    private static class ParamCloser implements QuietCloseable {
        public final long dict;

        ParamCloser(final long dict) {
            this.dict = dict;
        }

        @Override
        public void close() {
            if(dict != 0L)
                PythonAPI.pilecv4j_python_dict_destroy(dict);
        }
    }

    public void runPythonFunction(final String module, final String function, final Map<String, Object> kwds) throws PythonException {
        currentModule = module;
        currentFunction = function;
        try(final var q = new ParamCloser(PythonAPI.pilecv4j_python_dict_create());) {
            final long dict = fillPythonDict(q, kwds);
            throwIfNecessary(PythonAPI.pilecv4j_python_runPythonFunction(module, function, dict));
        }
    }

    public void addModulePath(final String dir) {
        final String absDir = FileSystems.getDefault().getPath(dir).normalize().toAbsolutePath().toString();
        PythonAPI.pilecv4j_python_addModulePath(absDir);
    }

    public KogMatResults sendMat(final CvMat mat, final boolean isRgb, final Map<String, Object> params) {
        if(imageSource != null)
            return imageSource.send(mat, isRgb, params);
        throw new IllegalStateException("There's no current image source");
    }

    public void eos() {
        sendMat(null, false, null);
    }

    public boolean sourceIsInitialized() {
        return imageSource != null;
    }

    public void waitUntilSourceInitialized(final long timeout) {
        final long startTime = System.currentTimeMillis();
        while(!sourceIsInitialized() && (System.currentTimeMillis() - startTime) < timeout)
            Thread.yield();

        if(!sourceIsInitialized())
            throw new PythonException(
                "The module \"" + currentModule + ".py\" using function \"" + currentFunction + "\" never initialized the image source. Did you call runPythonFunction somewhere?");
    }

    @Override
    public void close() {
        if(imageSource != null)
            imageSource.close();

        if(nativeObj != 0)
            PythonAPI.pilecv4j_python_kogSys_destroy(nativeObj);
    }

    private static final Object pythonExpandedLock = new Object();
    private static Map<String, File> pythonIsExpanded = new HashMap<>();

    public static Python initModule(final String pythonModuleUri) {
        final File pythonModulePath = unpackModule(pythonModuleUri);
        final Python ret = new Python();
        ret.addModulePath(pythonModulePath.getAbsolutePath());
        return ret;
    }

    public static File unpackModule(final String pythonModulePath) {
        synchronized(pythonExpandedLock) {
            final File ret = pythonIsExpanded.get(pythonModulePath);
            if(ret == null) {
                try (Vfs vfs = new Vfs();) {
                    final Path path = vfs.toPath(uncheck(() -> new URI(pythonModulePath)));
                    if(!path.exists() || !path.isDirectory())
                        throw new IllegalStateException("The python code isn't properly bundled in the jar file.");

                    final File pythonCodeDir = Files.createTempDirectory("pilecv4j-lib-python").toFile();
                    pythonCodeDir.deleteOnExit();

                    copy(path, pythonCodeDir.getAbsolutePath(), true);

                    pythonIsExpanded.put(pythonModulePath, pythonCodeDir);
                    return pythonCodeDir;
                } catch(final IOException ioe) {
                    throw new IllegalStateException("Failed to expand python code.", ioe);
                }
            }
            return ret;
        }
    }

    public static class KogMatResults implements QuietCloseable {
        private final long nativeObj;

        KogMatResults(final long nativeObj) {
            this.nativeObj = nativeObj;
        }

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

        @Override
        public void close() {
            if(nativeObj != 0L)
                PythonAPI.pilecv4j_python_kogMatResults_destroy(nativeObj);
        }

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

    public static class ImageSource implements QuietCloseable {
        private final long imageSourceRef;

        ImageSource(final long imageSourceRef) {
            this.imageSourceRef = imageSourceRef;
        }

        public KogMatResults send(final CvMat mat, final boolean isRgb) {
            return send(mat, isRgb, 0L);
        }

        public KogMatResults send(final CvMat mat, final boolean isRgb, final Map<String, Object> parameters) {
            if(parameters == null || parameters.size() == 0)
                return send(mat, isRgb, 0L);
            try(var q = new ParamCloser(PythonAPI.pilecv4j_python_dict_create());) {
                fillPythonDict(q, parameters);
                return send(mat, isRgb, q.dict);
            }
        }

        public long peek() {
            return PythonAPI.pilecv4j_python_imageSource_peek(imageSourceRef);
        }

        @Override
        public void close() {
            PythonAPI.pilecv4j_python_imageSource_destroy(imageSourceRef);
        }

        private KogMatResults send(final CvMat mat, final boolean isRgb, final long dictRef) {
            final long result;
            if(mat != null)
                result = PythonAPI.pilecv4j_python_imageSource_send(imageSourceRef, dictRef, mat.nativeObj, (isRgb ? 1 : 0));
            else
                result = PythonAPI.pilecv4j_python_imageSource_send(imageSourceRef, dictRef, 0L, 0);
            return (result == 0L) ? null : new KogMatResults(result);
        }
    }

    public int numModelLabels() {
        return PythonAPI.pilecv4j_python_kogSys_numModelLabels(nativeObj);
    }

    public String getModelLabel(final int i) {
        final Pointer ml = PythonAPI.pilecv4j_python_kogSys_modelLabel(nativeObj, i);
        if(Pointer.nativeValue(ml) == 0L)
            return null;
        else
            return ml.getString(0);
    }

    private static String stripTrailingSlash(final String path) {
        if(path.endsWith("/") || path.endsWith("\\"))
            return path.substring(0, path.length() - 1);
        else
            return path;
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

    private static long fillPythonDict(final ParamCloser q, final Map<String, Object> kwds) {
        final long dict;
        if(kwds != null && kwds.size() > 0) {
            dict = q.dict;
            kwds.entrySet().forEach(e -> {
                final Object val = e.getValue();
                if(val instanceof Python)
                    throwIfNecessary(PythonAPI.pilecv4j_python_dict_putKogSys(dict, e.getKey(), ((Python)val).nativeObj));
                else if(val instanceof Boolean)
                    throwIfNecessary(PythonAPI.pilecv4j_python_dict_putBoolean(dict, e.getKey(), ((Boolean)val).booleanValue() ? 1 : 0));
                else if(val instanceof Number) {
                    if(val instanceof Integer || val instanceof Byte || val instanceof Long)
                        throwIfNecessary(PythonAPI.pilecv4j_python_dict_putInt(dict, e.getKey(), ((Number)val).longValue()));
                    else if(val instanceof Float || val instanceof Double)
                        throwIfNecessary(PythonAPI.pilecv4j_python_dict_putFloat(dict, e.getKey(), ((Number)val).doubleValue()));
                    else
                        throw new PythonException("Unknown number type:" + val.getClass().getName());
                } else
                    throwIfNecessary(PythonAPI.pilecv4j_python_dict_putString(dict, e.getKey(), val.toString()));
            });
        } else
            dict = 0L;
        return dict;
    }

    private static void throwIfNecessary(final int status) throws PythonException {
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
}
