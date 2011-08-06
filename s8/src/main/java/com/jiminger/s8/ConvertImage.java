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

import java.awt.image.*;
import java.awt.image.renderable.*;
import java.io.*;
import javax.media.jai.*;
import com.sun.media.jai.codec.FileSeekableStream;

import com.jiminger.util.*;

public class ConvertImage
{
   public static final long megaBytes = 1024L * 1024L;
   public static final long defaultTileCacheSize = 300;
   public static long tileCacheSize = defaultTileCacheSize * megaBytes;

   private static String inputImageFilename;
   private static String outputImageFilename;
   private static String encoder;

   /** The main method. */
   public static void main(String[] args) 
      throws IOException, InterruptedException, Correlate.CorrelateException
   {
      // First parse the command line and test for various
      //  settings.
      if (!commandLine(args))
         System.exit(-1);

      // Set the tile cache up on JAI
      TileCache tc = JAI.createTileCache(tileCacheSize);
      JAI jai = JAI.getDefaultInstance();
      jai.setTileCache(tc);

      /*
       * Create an input stream from the specified file name
       * to be used with the file decoding operator.
       */
      FileSeekableStream stream = null;
      try {
         stream = new FileSeekableStream(inputImageFilename);
      } catch (IOException e) {
         e.printStackTrace();
         System.exit(0);
      }

      /* Create an operator to decode the image file. */
      RenderedImage inputImage = JAI.create("stream", stream);

      ParameterBlock pb = new ParameterBlock();
      pb.addSource(inputImage);
      pb.add(new Float(10.0));
      pb.add(new Float(10.0));
      // Perform the color conversion.
      RenderedImage processedImage = JAI.create("Scale", pb, null);

      JAI.create("filestore",processedImage,outputImageFilename, encoder, null);

   }

   static private void usage()
   {
      System.out.println(
         "usage: java [javaargs] ConvertImage -i inputImageFilename -o outputImageFilename -e ENCODER [-cs cacheSize]");
      System.out.println("  -cs image tile cache size in mega bytes. default is " + defaultTileCacheSize);
   }

   static private boolean commandLine(String[] args)
   {
      CommandLineParser cl = new CommandLineParser(args);

      // see if we are asking for help
      if (cl.getProperty("help") != null || 
          cl.getProperty("-help") != null)
      {
         usage();
         return false;
      }

      inputImageFilename = cl.getProperty("i");
      if (inputImageFilename == null)
      {
         usage();
         return false;
      }

      outputImageFilename = cl.getProperty("o");
      if (outputImageFilename == null)
      {
         usage();
         return false;
      }

      encoder = cl.getProperty("e");
      if (encoder == null)
      {
         usage();
         return false;
      }

      String tmps = cl.getProperty("cs");
      if (tmps != null)
         tileCacheSize = Long.parseLong(tmps) * megaBytes;

      return true;
   }

}
