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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class LibraryLoader {
    private LibraryLoader() {}

    static {
        List<InputStream> iss = null;

        try {
            iss = new ArrayList<>();
            try {
                final Enumeration<URL> systemResources = ClassLoader.getSystemResources("com.jiminger.lib.properties");
                while (systemResources.hasMoreElements()) {
                    URL el = systemResources.nextElement();
                    //System.out.println(el.getPath());
                    iss.add(el.openStream());
                }
            } catch (final IOException e) {
                throw new UnsatisfiedLinkError(
                        "Couldn't load the com.jiminger native library. Couldn't load the properties out of the jar:" + e.getLocalizedMessage());
            }

            // All we're going to do here is load the library from a jar file

            if (iss == null || iss.size() == 0)
                throw new UnsatisfiedLinkError(
                        "Couldn't load the com.jiminger native library. Is the jar file containing the library on the classpath?");

            for (InputStream is : iss) {
                final Properties libProps = new Properties();

                try {
                    libProps.load(is);
                } catch (final IOException e) {
                    throw new UnsatisfiedLinkError(
                            "Couldn't load the com.jiminger native library. Couldn't load the properties out of the jar:" + e.getLocalizedMessage());
                }

                IOUtils.closeQuietly(is);

                final String libName = libProps.getProperty("library");
                final String libSuffix = libName.substring(libName.lastIndexOf('.'));
                is = getInputStream(libName);
                if (is == null)
                    throw new UnsatisfiedLinkError("Couldn't load the library identified as the com.jiminger native library (" + libName + ").");

                File tmpFile = null;
                try {
                    tmpFile = File.createTempFile("com.jiminger", libSuffix);
                } catch (final IOException e) {
                    throw new UnsatisfiedLinkError(
                            "Couldn't load the com.jiminger native library. Couldn't copy the library out of the jar:" + e.getLocalizedMessage());
                }
                tmpFile.deleteOnExit();

                try {
                    FileUtils.copyInputStreamToFile(is, tmpFile);
                } catch (final IOException e) {
                    throw new UnsatisfiedLinkError(
                            "Couldn't load the com.jiminger native library. Couldn't copy the library out of the jar:" + e.getLocalizedMessage());
                }

                System.out.println("Loading \"" + libName + "\" as " + tmpFile.getAbsolutePath());
                System.load(tmpFile.getAbsolutePath());
            }
        } finally {
            for (final InputStream is : iss)
                if (is != null)
                    IOUtils.closeQuietly(is);
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
