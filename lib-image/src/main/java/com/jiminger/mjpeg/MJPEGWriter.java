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


package com.jiminger.mjpeg;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.media.jai.JAI;

import com.jiminger.util.CommandLineParser;
import com.jiminger.util.LibraryLoader;
import com.sun.media.jai.codec.FileSeekableStream;

public class MJPEGWriter
{
   static public String parentDir = null;
   static public String avifile = "out.avi";
   public static int avifps = 16;

   public static void main(String [] args)
   {
      if (!commandLine(args))
         System.exit(-1);

      // assume args are file names
      initializeMJPEG(avifile);
      boolean working = true;
      File pdir = new File(parentDir);
      if (!pdir.isDirectory())
      {
         System.out.println("\"" + parentDir + "\" is not a directory.");
         usage();
      }

      File[] files = pdir.listFiles(
         new FileFilter()
         {
            public boolean accept(File f) 
            { 
               String fp = f.getAbsolutePath();
               return f.isFile() && (fp.endsWith(".jpeg") || fp.endsWith(".JPEG") || 
                                     fp.endsWith("jpg") || fp.endsWith("JPG"));
            }
         }
         );
      
      List<File> fileList = Arrays.asList(files);
      Collections.sort(fileList, new Comparator<File>()
      {
         @Override
         public int compare(File o1, File o2)
         {
            return o1.getName().compareTo(o2.getName());
         }
         
      });

      
      for (File f : fileList)
         working = appendFile(f.getAbsolutePath());

      if (working)
         close(avifps);
      else
         System.out.println("Failed to create AVI - Who knows why!");

      cleanUp();
   }

   private static void usage()
   {
      System.out.println("usage: java [javaargs] com.jiminger.mjpeg.MJPEGWriter -pdir parentDir [-avifile out.avi] [-avifps 16]");
   }

   public static boolean commandLine(String [] args)
   {
      CommandLineParser cl = new CommandLineParser(args);
      // see if we are asking for help
      if (cl.getProperty("help") != null || 
          cl.getProperty("-help") != null)
      {
         usage();
         return false;
      }

      parentDir = cl.getProperty("pdir");
      if (parentDir == null)
      {
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

   static public boolean appendFile(String filename)
   {
//      System.out.println("File: " + filename);
      if (height == -1)
      {
         FileSeekableStream stream = null;
         try 
         {
            stream = new FileSeekableStream(filename);

            RenderedImage origImage = JAI.create("stream", stream);
            width = origImage.getWidth();
            height = origImage.getHeight();

            stream.close();
         } 
         catch (IOException e)
         {
            return false;
         }

      }

      return doappendFile(filename,width,height);
   }

   static {
      LibraryLoader.loadLibrary("jiminger");
   }

   static private native boolean doappendFile(String filename, int width, int height);

   static public native boolean close(int fps);

   static public native void cleanUp();
}
