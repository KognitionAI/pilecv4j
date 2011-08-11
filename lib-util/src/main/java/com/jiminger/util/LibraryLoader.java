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
import java.util.Properties;

import org.apache.commons.io.FileUtils;

public class LibraryLoader
{
	static {
		// all we're going to do here is load the library from a jar file
		InputStream is = ClassLoader.getSystemResourceAsStream("com.jiminger.lib.properties");
		if (is == null)
			throw new UnsatisfiedLinkError("Couldn't load the com.jiminger native library. Is the jar file containing the library on the classpath?");
		
		Properties libProps = new Properties();
		
		try 
		{
			libProps.load(is);
		} 
		catch (IOException e) 
		{
			throw new UnsatisfiedLinkError("Couldn't load the com.jiminger native library. Couldn't load the properties out of the jar:" + e.getLocalizedMessage());
		}
		
		String libName = libProps.getProperty("library");
		String libSuffix = libProps.getProperty("suffix");
		is = ClassLoader.getSystemResourceAsStream(libName);
		if (is == null)
			throw new UnsatisfiedLinkError("Couldn't load the library identified as the com.jiminger native library (" + libName + ").");
		
		File tmpFile = null;
		try {
			tmpFile = File.createTempFile("com.jiminger", libSuffix);
		} catch (IOException e) {
			throw new UnsatisfiedLinkError("Couldn't load the com.jiminger native library. Couldn't copy the library out of the jar:" + e.getLocalizedMessage());
		}
		
		try {
			FileUtils.copyInputStreamToFile(is, tmpFile);
		} catch (IOException e) {
			throw new UnsatisfiedLinkError("Couldn't load the com.jiminger native library. Couldn't copy the library out of the jar:" + e.getLocalizedMessage());
		}
		
		System.out.println("Loading:" + tmpFile.getAbsolutePath());
		System.load(tmpFile.getAbsolutePath());
	}
}
