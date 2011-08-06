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

package com.jiminger.image;

import java.awt.image.renderable.ParameterBlock;
import java.io.*;
import javax.media.jai.*;

import com.jiminger.util.CommandLineParser;

public class ImageConvert
{
   public static final long megaBytes = 1024L * 1024L;
   public static final long defaultTileCacheSize = 300;
   public static final String defaultDestFilename = "tmp.bmp";
   public static final String defaultDestFileType = "BMP";

   public static long tileCacheSize = defaultTileCacheSize * megaBytes;

   public static String sourceFileName = null;
   public static String destFileName = null;
   public static String destFileType = null;

   /** The main method. */
   public static void main(String[] args) 
      throws IOException
   {
      // First parse the command line and test for various
      //  settings.
      if (!commandLine(args))
         return;

      // Set the tile cache up on JAI
      TileCache tc = JAI.createTileCache(tileCacheSize);
      JAI jai = JAI.getDefaultInstance();
      jai.setTileCache(tc);

      ParameterBlock pb = new ParameterBlock();
      pb.add(sourceFileName);
      pb.add(null);
      pb.add(null);
      RenderedOp image = JAI.create("fileload",pb);
//      RenderedOp image = JAI.create("fileload",sourceFileName, null, null);
      JAI.create("filestore",image,destFileName, destFileType, null);
      
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

      sourceFileName = cl.getProperty("f");
      if (sourceFileName == null)
      {
         usage();
         return false;
      }

      String tmps = cl.getProperty("cs");
      if (tmps != null)
         tileCacheSize = Long.parseLong(tmps) * megaBytes;

      destFileName = cl.getProperty("o");
      if (destFileName == null)
         destFileName = defaultDestFilename;

      destFileType = cl.getProperty("t");
      if (destFileType == null)
         destFileType = defaultDestFileType;

      return true;
   }

   private static void usage()
   {
      System.out.println(
         "usage: java [javaargs] ImageConvert -f filename [-o " +
         defaultDestFilename + "] [-cs " +
         defaultTileCacheSize + "] [-t " + defaultDestFileType + "]");
      System.out.println();
      System.out.println("  -f this is how you supply filename of the source image.");
      System.out.println("  -o destination image filename.");
      System.out.println("  -cs image tile cache size in mega bytes.");
      System.out.println("  -t File type of the destination image.");
   }
}
