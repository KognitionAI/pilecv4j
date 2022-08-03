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

package ai.kognition.pilecv4j.image.mjpeg;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import net.dempsy.util.CommandLineParser;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageAPI;

public class MJPEGWriter {
    static {
        CvMat.initOpenCv();
    }

    static public File pdir = null;
    static public String avifile = "out.avi";
    public static int avifps = 16;

    public static void main(final String[] args) {
        if(!commandLine(args))
            System.exit(-1);

        // assume args are file names
        initializeMJPEG(avifile);
        boolean working = true;

        final File[] files = pdir.listFiles(
            f -> {
                final String fp = f.getAbsolutePath();
                return f.isFile() && (fp.endsWith(".jpeg") || fp.endsWith(".JPEG") ||
                    fp.endsWith("jpg") || fp.endsWith("JPG"));
            });

        final List<File> fileList = Arrays.asList(files);
        Collections.sort(fileList, (o1, o2) -> o1.getName().compareTo(o2.getName()));

        for(final File f: fileList)
            working = appendFile(f.getAbsolutePath());

        if(working)
            close(avifps);
        else
            System.out.println("Failed to create AVI - Who knows why!");

        cleanUp();
    }

    public static boolean initializeMJPEG(final String filename) {
        return ImageAPI.pilecv4j_image_mjpeg_initializeMJPEG(filename) == 0 ? false : true;
    }

    public static boolean doappendFile(final String filename, final int width, final int height) {
        return ImageAPI.pilecv4j_image_mjpeg_doappendFile(filename, width, height) == 0 ? false : true;
    }

    public static boolean close(final int fps) {
        return ImageAPI.pilecv4j_image_mjpeg_close(fps) == 0 ? false : true;
    }

    public static void cleanUp() {
        ImageAPI.pilecv4j_image_mjpeg_cleanUp();
    }

    private static void usage() {
        System.out.println("usage: java [javaargs] " + MJPEGWriter.class.getName() + " -pdir parentDir [-avifile out.avi] [-avifps 16]");
    }

    public static boolean commandLine(final String[] args) {
        final CommandLineParser cl = new CommandLineParser(args);
        // see if we are asking for help
        if(cl.getProperty("help") != null ||
            cl.getProperty("-help") != null) {
            usage();
            return false;
        }

        final String parentDir = cl.getProperty("pdir");
        if(parentDir == null) {
            usage();
            return false;
        }

        pdir = new File(parentDir);
        if(!pdir.isDirectory()) {
            System.out.println("\"" + parentDir + "\" is not a directory.");
            usage();
            return false;
        }

        String tmps = cl.getProperty("avifile");
        if(tmps != null)
            avifile = tmps;

        tmps = cl.getProperty("avifps");
        if(tmps != null)
            avifps = Integer.parseInt(tmps);

        return true;
    }

    static private int width = -1;
    static private int height = -1;

    static public boolean appendFile(final String filename) {
        if(height == -1) {
            final Mat origImage = Imgcodecs.imread(filename);
            width = origImage.cols();
            height = origImage.rows();
        }
        return doappendFile(filename, width, height);
    }
}
