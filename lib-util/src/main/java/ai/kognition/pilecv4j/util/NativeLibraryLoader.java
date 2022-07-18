/***********************************************************************
 * Legacy Film to DVD Project
 * Copyright (C) 2005 James F. Carroll
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 ****************************************************************************/

package ai.kognition.pilecv4j.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will load native libraries from a jar file as long as they've been packaged appropriately.
 */
public class NativeLibraryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeLibraryLoader.class);

    private static Set<String> loaded = new HashSet<>();

    private static PlatformDetection platform = new PlatformDetection();

    public static class Loader {
        private final List<LibraryDefinition> libs = new ArrayList<>();
        private final List<LibraryLoadCallback> preLoadCallbacks = new ArrayList<>();
        private final List<LibraryLoadCallback> postLoadCallbacks = new ArrayList<>();
        private File destinationDir = new File(System.getProperty("java.io.tmpdir"));

        private static class LibraryDefinition {
            final private boolean required;
            final private String libName;

            private LibraryDefinition(final boolean required, final String libName) {
                super();
                this.required = required;
                this.libName = libName;
            }
        }

        public Loader library(final String... libNames) {
            Arrays.stream(libNames)
                .map(ln -> new LibraryDefinition(true, ln))
                .forEach(libs::add);
            return this;
        }

        public Loader optional(final String... libNames) {
            Arrays.stream(libNames)
                .map(ln -> new LibraryDefinition(false, ln))
                .forEach(libs::add);
            return this;
        }

        @FunctionalInterface
        public interface LibraryLoadCallback {
            public void loading(File directory, String libName, String fullLibName);
        }

        @FunctionalInterface
        @Deprecated
        public interface PreLibraryLoad extends LibraryLoadCallback {}

        public Loader addPreLoadCallback(final LibraryLoadCallback ll) {
            preLoadCallbacks.add(ll);
            return this;
        }

        public Loader addPostLoadCallback(final LibraryLoadCallback ll) {
            postLoadCallbacks.add(ll);
            return this;
        }

        @Deprecated
        public Loader addCallback(final PreLibraryLoad ll) {
            return addPreLoadCallback(ll);
        }

        public Loader destinationDir(final String destinationDir) {
            this.destinationDir = new File(destinationDir);
            return this;
        }

        public void load() {
            if(!this.destinationDir.exists()) {
                if(!this.destinationDir.mkdirs())
                    LOGGER.warn("FAILED to create the destination directory \"{}\" for the libraries {}",
                        this.destinationDir, libs.stream().map(l -> l.libName).collect(Collectors.toList()));
            }

            final File tmpDir = this.destinationDir;
            libs.stream()
                .filter(ld -> ld != null)
                .filter(ld -> ld.libName != null)
                .filter(ld -> {
                    final boolean needsLoading = !loaded.contains(ld.libName);
                    if(!needsLoading)
                        LOGGER.debug("Native library \"" + ld.libName + "\" is already loaded.");
                    return needsLoading;
                })
                .forEach(ld -> {
                    final String libFileName = System.mapLibraryName(ld.libName);
                    final String libMD5FileName = libFileName + ".MD5";

                    LOGGER.trace("Native library \"" + ld.libName + "\" platform specific file name is \"" + libFileName + "\"");
                    final File libFile = new File(tmpDir, libFileName);
                    final File libMD5File = new File(tmpDir, libMD5FileName);
                    boolean loadMe = true;
                    if(!libFile.exists())
                        loadMe = copyFromJar(ld, libFileName, libFile, libMD5FileName, libMD5File);
                    else {
                        final boolean copyMeFromJar;
                        final String fileMD5 = rethrowIOException(
                            () -> (libMD5File.exists()) ? FileUtils.readFileToString(libMD5File, StandardCharsets.UTF_8.name()) : (String)null,
                            libMD5FileName);
                        // if the file exists then fileMD5 is set. Otherwise it's null.
                        if(fileMD5 != null) {
                            // read the MD5 from the jar.
                            final String jarMD5 = rethrowIOException(() -> {
                                try(InputStream is = getInputStream(platform + "/" + libMD5FileName)) {
                                    if(is == null) {
                                        LOGGER.info("The library \"{}\" doesn't appear to have a coresponding MD5. Reloading from jar file.", libFileName);
                                        return null;
                                    } else
                                        return IOUtils.toString(is, StandardCharsets.UTF_8.name());
                                }
                            }, platform + "/" + libMD5FileName);
                            // if the fileMD5 contents doesn't equal the jarMD5 contents then we need to
                            // re-copy the library from the jar file.
                            copyMeFromJar = (!fileMD5.equals(jarMD5));
                        } else {
                            // if there is not fileMD5 then we're just going to re-copy from the jar
                            LOGGER.warn("Missing MD5 file for \"{}.\" This will result in recopying of the library file every startup." +
                                " Consider generating an MD5 file for the library");
                            copyMeFromJar = true;
                        }

                        if(copyMeFromJar)
                            loadMe = copyFromJar(ld, libFileName, libFile, libMD5FileName, libMD5File);
                        else
                            LOGGER.debug("Native library \"" + ld.libName + "\" is already on the filesystem. Not overwriting.");
                    }
                    if(loadMe) {
                        preLoadCallbacks.stream()
                            .forEach(ll -> ll.loading(tmpDir, ld.libName, libFileName));
                        System.load(libFile.getAbsolutePath());
                        postLoadCallbacks.stream()
                            .forEach(ll -> ll.loading(tmpDir, ld.libName, libFileName));
                    }
                    loaded.add(ld.libName);
                });
        }

    }

    private static boolean copyFromJar(final Loader.LibraryDefinition ld, final String libFileName, final File libFile, final String libMD5FileName,
        final File libMD5File) throws UnsatisfiedLinkError {
        final String libFilePath = platform + "/" + libFileName;
        final String libMD5FilePath = platform + "/" + libMD5FileName;
        LOGGER.debug("Copying native library \"" + libFilePath + "\" from the jar file.");
        final boolean loadMe = rethrowIOException(() -> {
            try(InputStream is = getInputStream(libFilePath)) {
                if(is == null) {
                    if(ld.required)
                        throw new UnsatisfiedLinkError(
                            "Required native library \"" + ld.libName + "\" with platform representation \"" + libFilePath
                                + "\" doesn't appear to exist in any jar file on the classpath");
                    else {
                        // if we're not required and it's missing, we're fine
                        LOGGER.debug("Requested but optional library \"" + ld.libName + "\" is not on the classpath.");
                        return false;
                    }
                }
                FileUtils.copyInputStreamToFile(is, libFile);
                return true;
            }
        }, libFilePath);

        if(loadMe) // loadMe is only set if the library was in the jar (and copied onto the disk).
            // otherwise we can just skip trying to load the MD5
            rethrowIOException(() -> {
                try(InputStream is = getInputStream(libMD5FilePath)) {
                    if(is == null) {
                        LOGGER.info("The library \"{}\" doesn't appear to have a coresponding MD5. Reloading from jar file.", libFilePath);
                    } else {
                        FileUtils.copyInputStreamToFile(is, libMD5File);
                    }
                }
            }, libMD5FilePath);

        return loadMe;
    }

    public static Loader loader() {
        return new Loader();
    }

    private NativeLibraryLoader() {}

    @FunctionalInterface
    private static interface SupplierThrows<R, E extends Throwable> {
        public R get() throws E;
    }

    @FunctionalInterface
    private static interface Nothing<E extends Throwable> {
        public void doIt() throws E;
    }

    private static <R> R rethrowIOException(final SupplierThrows<R, IOException> suppl, final String libName) {
        try {
            return suppl.get();
        } catch(final IOException ioe) {
            final String message = "Couldn't load the file from the jar (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }
    }

    private static void rethrowIOException(final Nothing<IOException> suppl, final String libName) {
        try {
            suppl.doIt();
        } catch(final IOException ioe) {
            final String message = "Couldn't load the file from the jar (" + libName + "):";
            LOGGER.error(message, ioe);
            throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
        }
    }

    private static InputStream getInputStream(final String resource) {
        // I need to find the library. Let's start with the "current" classloader.
        // see http://www.javaworld.com/javaworld/javaqa/2003-06/01-qa-0606-load.html
        // also see: http://www.javaworld.com/javaworld/javaqa/2003-03/01-qa-0314-forname.html
        InputStream is = getInputStreamFromClassLoader(NativeLibraryLoader.class.getClassLoader(), resource);
        if(is == null) // ok, now try the context classloader
            is = getInputStreamFromClassLoader(Thread.currentThread().getContextClassLoader(), resource);
        if(is == null) // finally try the system classloader though if we're here we're probably screwed
            is = getInputStreamFromClassLoader(ClassLoader.getSystemClassLoader(), resource);

        return is;
    }

    private static InputStream getInputStreamFromClassLoader(final ClassLoader loader, final String resource) {
        if(loader == null)
            return null;
        InputStream is = loader.getResourceAsStream(resource);
        if(is == null)
            is = loader.getResourceAsStream("/" + resource);

        return is;
    }

}
