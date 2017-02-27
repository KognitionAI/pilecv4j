package com.jiminger.s8;
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

import java.io.IOException;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import com.jiminger.util.CommandLineParser;

public class ConvertImage {
    private static String inputImageFilename;
    private static String outputImageFilename;

    /** The main method. */
    public static void main(final String[] args)
            throws IOException, InterruptedException, Correlate.CorrelateException {
        // First parse the command line and test for various
        // settings.
        if (!commandLine(args))
            System.exit(-1);

        final Mat img = Imgcodecs.imread(inputImageFilename);
        final Mat processedImage = new Mat();
        Imgproc.resize(img, processedImage, new Size(img.cols() / 10.0, img.rows() / 10.0));
        Imgcodecs.imwrite(outputImageFilename, processedImage);
    }

    static private void usage() {
        System.out.println(
                "usage: java [javaargs] ConvertImage -i inputImageFilename -o outputImageFilename");
    }

    static private boolean commandLine(final String[] args) {
        final CommandLineParser cl = new CommandLineParser(args);

        // see if we are asking for help
        if (cl.getProperty("help") != null ||
                cl.getProperty("-help") != null) {
            usage();
            return false;
        }

        inputImageFilename = cl.getProperty("i");
        if (inputImageFilename == null) {
            usage();
            return false;
        }

        outputImageFilename = cl.getProperty("o");
        if (outputImageFilename == null) {
            usage();
            return false;
        }

        return true;
    }

}
