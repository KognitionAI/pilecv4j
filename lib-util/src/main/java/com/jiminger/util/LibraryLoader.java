/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

package com.jiminger.util;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryLoader.class);

    private LibraryLoader() {}

    static {
        List<URL> iss = null;

        iss = new ArrayList<>();
        try {
            final Enumeration<URL> systemResources = ClassLoader.getSystemResources("com.jiminger.lib.properties");
            while (systemResources.hasMoreElements())
                iss.add(systemResources.nextElement());
        } catch (final IOException e) {
            final String message = "Couldn't load the com.jiminger native library. Couldn't find the com.jiminger.lib.properties files on the classpath:";
            LOGGER.error(message, e);
            throw new UnsatisfiedLinkError(message + e.getLocalizedMessage());
        }

        // All we're going to do here is load the library from a jar file

        if (iss == null || iss.size() == 0) {
            LOGGER.warn("Couldn't find any com.jiminger native libraries.");
        } else {

            try {
                final MessageDigest digest = java.security.MessageDigest.getInstance("MD5");

                for (final URL propFile : iss) {
                    final Properties libProps = new Properties();

                    try (InputStream propFileIs = propFile.openStream()) {
                        libProps.load(propFileIs);
                    } catch (final IOException e) {
                        final String message = "Couldn't load the com.jiminger native library. Couldn't load the properties out of the jar:";
                        LOGGER.error(message, e);
                        throw new UnsatisfiedLinkError(message + e.getLocalizedMessage());
                    }

                    final String libName = libProps.getProperty("library");
                    final String libSuffix = libName.substring(libName.lastIndexOf('.'));
                    File tmpFile = null;
                    try (InputStream is = new DigestInputStream(getInputStream(libName), digest);) {
                        try {
                            tmpFile = File.createTempFile("com.jiminger", libSuffix);
                        } catch (final IOException e) {
                            final String message = "Couldn't load the com.jiminger native library. Couldn't copy the library out of the jar:";
                            LOGGER.error(message, e);
                            throw new UnsatisfiedLinkError(message + e.getLocalizedMessage());
                        }

                        try {
                            FileUtils.copyInputStreamToFile(is, tmpFile);
                        } catch (final IOException e) {
                            final String message = "Couldn't load the com.jiminger native library. Couldn't copy the library out of the jar:";
                            LOGGER.error(message, e);
                            throw new UnsatisfiedLinkError(message + e.getLocalizedMessage());
                        }
                    } catch (final IOException ioe) {
                        final String message = "Couldn't load the library identified as the com.jiminger native library (" + libName + "):";
                        LOGGER.error(message, ioe);
                        throw new UnsatisfiedLinkError(message + ioe.getLocalizedMessage());
                    }

                    final String md5 = StringUtils.bytesToHex(digest.digest());
                    final File tmpDir = tmpFile.getParentFile();
                    final String fname = "com.jiminger." + libName + "." + md5 + "." + libSuffix;
                    final File libFile = new File(tmpDir, fname);
                    if (libFile.exists()) {
                        boolean theSame = false;

                        // make sure it has the same md5.
                        if (libFile.length() == tmpFile.length()) {
                            // check the md5
                            final long numBytes = libFile.length();
                            final MessageDigest checkDigest = java.security.MessageDigest.getInstance("MD5");
                            try (final InputStream is = new DigestInputStream(new BufferedInputStream(new FileInputStream(libFile)), checkDigest)) {
                                for (long i = 0; i < numBytes; i++)
                                    if (is.read() == -1)
                                        throw new EOFException();
                            } catch (final IOException ioe) {
                                LOGGER.warn("Existing lib file read failed. Attempting to simply overwrite it.");
                            }

                            final String libMd5 = StringUtils.bytesToHex(checkDigest.digest());

                            if (libMd5.equals(md5))
                                theSame = true;
                        }

                        if (theSame) {
                            if (!tmpFile.delete())
                                LOGGER.warn("Couldn't delete temporary file:" + tmpFile.getAbsolutePath());
                        } else {
                            // attempt to overwrite.
                            if (!libFile.delete()) {
                                final String message = "Couldn't delete the existing library file but it's not correct.";
                                LOGGER.error(message);
                                throw new UnsatisfiedLinkError(message);
                            }

                            if (!tmpFile.renameTo(libFile)) {
                                final String message = "Failed to rename the library file from \"" + tmpFile.getAbsolutePath() + "\" to \""
                                        + libFile.getAbsolutePath() + "\".";
                                LOGGER.error(message);
                                throw new UnsatisfiedLinkError(message);
                            }
                        }
                    } else {
                        if (!tmpFile.renameTo(libFile)) {
                            final String message = "Failed to rename the library file from \"" + tmpFile.getAbsolutePath() + "\" to \""
                                    + libFile.getAbsolutePath() + "\".";
                            LOGGER.error(message);
                            throw new UnsatisfiedLinkError(message);
                        }
                    }
                    LOGGER.debug("Loading \"{}\" as \"{}\"", libName, libFile.getAbsolutePath());
                    System.load(libFile.getAbsolutePath());
                }
            } catch (final NoSuchAlgorithmException nsa) {
                final String message = "Missing MD5 algorithm.";
                LOGGER.error(message);
                throw new UnsatisfiedLinkError(message);
            }
        }
    }

    public static void init() {}

    private static InputStream getInputStream(final String resource) {
        // I need to find the library. Let's start with the "current" classloader.
        // see http://www.javaworld.com/javaworld/javaqa/2003-06/01-qa-0606-load.html
        // also see: http://www.javaworld.com/javaworld/javaqa/2003-03/01-qa-0314-forname.html
        InputStream is = getInputStreamFromClassLoader(LibraryLoader.class.getClassLoader(), resource);
        if (is == null) // ok, now try the context classloader
            is = getInputStreamFromClassLoader(Thread.currentThread().getContextClassLoader(), resource);
        if (is == null) // finally try the system classloader though if we're here we're probably screwed
            is = getInputStreamFromClassLoader(ClassLoader.getSystemClassLoader(), resource);

        return is;
    }

    private static InputStream getInputStreamFromClassLoader(final ClassLoader loader, final String resource) {
        InputStream is = loader.getResourceAsStream(resource);
        if (is == null)
            is = loader.getResourceAsStream("/" + resource);

        return is;
    }
}
