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

package com.jiminger.image.mjpeg;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import com.jiminger.util.CommandLineParser;

import net.dempsy.util.library.NativeLibraryLoader;

public class MJPEGWriter {
    static {
        NativeLibraryLoader.init();
    }

    static public String parentDir = null;
    static public String avifile = "out.avi";
    public static int avifps = 16;

    public static void main(final String[] args) {
        if (!commandLine(args))
            System.exit(-1);

        // assume args are file names
        initializeMJPEG(avifile);
        boolean working = true;
        final File pdir = new File(parentDir);
        if (!pdir.isDirectory()) {
            System.out.println("\"" + parentDir + "\" is not a directory.");
            usage();
        }

        final File[] files = pdir.listFiles(
                new FileFilter() {
                    @Override
                    public boolean accept(final File f) {
                        final String fp = f.getAbsolutePath();
                        return f.isFile() && (fp.endsWith(".jpeg") || fp.endsWith(".JPEG") ||
                                fp.endsWith("jpg") || fp.endsWith("JPG"));
                    }
                });

        final List<File> fileList = Arrays.asList(files);
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(final File o1, final File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        for (final File f : fileList)
            working = appendFile(f.getAbsolutePath());

        if (working)
            close(avifps);
        else
            System.out.println("Failed to create AVI - Who knows why!");

        cleanUp();
    }

    private static void usage() {
        System.out.println("usage: java [javaargs] com.jiminger.mjpeg.MJPEGWriter -pdir parentDir [-avifile out.avi] [-avifps 16]");
    }

    public static boolean commandLine(final String[] args) {
        final CommandLineParser cl = new CommandLineParser(args);
        // see if we are asking for help
        if (cl.getProperty("help") != null ||
                cl.getProperty("-help") != null) {
            usage();
            return false;
        }

        parentDir = cl.getProperty("pdir");
        if (parentDir == null) {
            usage();
            return false;
        }

        String tmps = cl.getProperty("avifile");
        if (tmps != null)
            avifile = tmps;

        tmps = cl.getProperty("avifps");
        if (tmps != null)
            avifps = Integer.parseInt(tmps);

        return true;
    }

    static public native boolean initializeMJPEG(String filename);

    static private int width = -1;
    static private int height = -1;

    static public boolean appendFile(final String filename) {
        if (height == -1) {
            final Mat origImage = Imgcodecs.imread(filename);
            width = origImage.cols();
            height = origImage.rows();
        }
        return doappendFile(filename, width, height);
    }

    static private native boolean doappendFile(String filename, int width, int height);

    static public native boolean close(int fps);

    static public native void cleanUp();
}
