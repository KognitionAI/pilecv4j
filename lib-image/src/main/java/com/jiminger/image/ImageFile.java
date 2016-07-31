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

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import com.jiminger.image.drawing.Utils;

public class ImageFile
{
   public static BufferedImage readImageFile(String filename) throws IOException {
      File f = new File(filename);
      BufferedImage ret = ImageIO.read(f);
      if (ret == null) {
    	  System.out.println("Failed to read '" + filename + "' using ImageIO");
    	  Mat mat = Imgcodecs.imread(filename, Imgcodecs.IMREAD_ANYCOLOR);
    	  if (mat == null)
    		  throw new IllegalArgumentException("Can't read '" + filename + "' as an image. No codec available in either ImageIO or OpenCv");
    	  if (filename.endsWith(".jp2"))
    		  Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
    	  ret = Utils.mat2Img(mat);
      }
      return ret;
   }

   public static void writeImageFile(BufferedImage ri, String filename, String format) throws IOException
   {
      File f = new File(filename);
      // make sure the output directory exists.
      //      f.mkdirs();
      Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(format);
      if (!iter.hasNext())
         throw new IOException("Can't write image of type " + format);
      ImageWriter writer = (ImageWriter)iter.next(); // grab the first one
      ImageOutputStream ios = ImageIO.createImageOutputStream(f);
      ImageWriteParam param = writer.getDefaultWriteParam();

      writer.setOutput(ios);

      writer.write(null, new IIOImage(ri,null,null), param);
      ios.flush();
      ios.close();
   }
   
   public static BufferedImage convert(Image im)
   {
      if (im instanceof BufferedImage)
         return (BufferedImage) im;
      BufferedImage bi = new BufferedImage(im.getWidth(null),im.getHeight(null),BufferedImage.TYPE_INT_RGB);
      Graphics bg = bi.getGraphics();
      bg.drawImage(im, 0, 0, null);
      bg.dispose();
      return bi;
   }

   public static void writeImageFile(BufferedImage ri, String filename)
      throws IOException
   {
      int dotindex = filename.lastIndexOf(".");
      if (dotindex < 0)
         throw new IOException("No extention on " + filename);
      String ext = filename.substring(dotindex + 1);

      File f = new File(filename).getCanonicalFile();
      File p = f.getParentFile();
      // make sure the output directory exists.
      p.mkdirs();
      Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix(ext);
      if (!iter.hasNext())
         throw new IOException("Can't write with extention " + ext);
      ImageWriter writer = (ImageWriter)iter.next(); // grab the first one
      ImageOutputStream ios = ImageIO.createImageOutputStream(f);
      ImageWriteParam param = writer.getDefaultWriteParam();

      writer.setOutput(ios);

      writer.write(null, new IIOImage(ri,null,null), param);
      ios.flush();
      ios.close();
   }

   public static void transcode(BufferedImage bi,ImageDestinationDefinition dest)
      throws IOException
   {
      if (infile != null && infile.equalsIgnoreCase(dest.outfile))
         throw new IOException("Can't overwrite original file durring transcode (" + infile + ").");
      
      if (dest.maxw != -1 || dest.maxh != -1)
      {
         int width = bi.getWidth();
         int height = bi.getHeight();
         
         double scale = -1.0;
         if (dest.maxh != -1) {
            if (height > dest.maxh)
               // see what we need to scale to make the height the same.
               scale = ((double)dest.maxh) / ((double)height);
         }
         
         if (dest.maxw != -1)
         {
            int adjwidth = (scale >= 0.0) ? (int)Math.round(scale * (double)width) : width;
            if (adjwidth > dest.maxw)
            {
               scale = ((double)dest.maxw) / ((double)adjwidth);
            }
         }
         
         if (scale >= 0.0)
         {
         
            int newwidth = (int)Math.round(scale * ((double)width));
            int newheight = (int)Math.round(scale * ((double)height));
         
            bi = convert(bi.getScaledInstance(newwidth, newheight, BufferedImage.SCALE_DEFAULT));
         }
      }

      if (dest.format == null)
         writeImageFile(bi,dest.outfile);
      else
         writeImageFile(bi,dest.outfile,dest.format);
   }

   public static class ImageDestinationDefinition
   {
      public String outfile = null;
      public String format = null;
      public int maxw = -1;
      public int maxh = -1;
      public boolean verify = false;
      
      public void set() {}
   }
   
   public static String infile = null;
   
   public static void main(String [] args)
      throws IOException
   {
      List<ImageDestinationDefinition> dests = commandLine(args);
      if (dests == null || dests.size() == 0)
      {
         usage();
         return;
      }
      
      if (infile == null)
      {
         usage();
         return;
      }
      
      BufferedImage image = readImageFile(infile);

      for (ImageDestinationDefinition dest : dests)
      {
         transcode(image,dest);

         if (dest.verify)
         {
            RenderedImage im = readImageFile(dest.outfile);
            int width2 = im.getWidth();
            int height2 = im.getHeight();

            if (dest.maxw != width2 ||
                  dest.maxh != height2)
               throw new IOException("Verification failed!");
         }
      }
   }

   static private List<ImageDestinationDefinition> commandLine(String[] args)
   {
      List<ImageDestinationDefinition> ret = new ArrayList<ImageDestinationDefinition>();
      ImageDestinationDefinition cur = null;
      
      for (int i = 0; i < args.length; i++)
      {
         String optionArg = args[i];
         // see if we are asking for help
         if ("help".equalsIgnoreCase(optionArg) || 
               "-help".equalsIgnoreCase(optionArg))
         {
            usage();
            return null;
         }

         if ("-i".equalsIgnoreCase(optionArg))
         {
            if (infile != null)
            {
               System.err.println("One infile only");
               usage();
               return null;
            }
            infile = args[i+1];
            i++;
         }
         
         else if ("-o".equalsIgnoreCase(args[i]))
         {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if (cur.outfile != null)
               cur = push(cur,ret);
            cur.outfile = args[i+1];
            i++;
         }
            
         else if ("-f".equalsIgnoreCase(args[i]))
         {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if (cur.format != null)
               cur = push(cur,ret);
            cur.format = args[i+1];
            i++;
         }
         
         else if ("-verify".equalsIgnoreCase(args[i]))
         {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if (cur.verify == false)
               cur = push(cur,ret);
            cur.verify = true;
         }
         else if ("-maxw".equalsIgnoreCase(args[i]))
         {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if (cur.maxw != -1)
               cur = push(cur,ret);
            cur.maxw = Integer.parseInt(args[i+1]);
            i++;
         }
         else if ("-maxh".equalsIgnoreCase(args[i]))
         {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if (cur.maxh != -1)
               cur = push(cur,ret);
            cur.maxh = Integer.parseInt(args[i+1]);
            i++;
         }
         else
         {
            usage();
            return null;
         }
      }
      
      if (cur != null) 
      {
         cur.set();
         ret.add(cur);
      }
      
      return ret;
   }
         
   static private ImageDestinationDefinition push(ImageDestinationDefinition cur,
         List<ImageDestinationDefinition> ret)
   {
      ret.add(cur);
      cur.set();
      return new ImageDestinationDefinition();
   }

   static private void usage()
   {
      System.out.println("usage: java [javaargs] ImageFile -i infile -o outfile [-f format] [-maxw width] [-maxh height] [-verify]");
      System.out.println("       options -o through -verify can be repeated to convert an image file");
      System.out.println("       to a number of different formats and dimentions");
   }
   
//   private static BufferedImage fromRenderedToBuffered(RenderedImage img) {
//      if (img instanceof BufferedImage) {
//         return (BufferedImage) img;
//      }
//
//      ColorModel     cm = img.getColorModel();
//      int            w  = img.getWidth();
//      int            h  = img.getHeight();
//      WritableRaster raster = cm.createCompatibleWritableRaster(w,h);
//      boolean        isAlphaPremultiplied = cm.isAlphaPremultiplied();
//      Hashtable<String,Object>      props = new Hashtable<String,Object>();
//      String []      keys = img.getPropertyNames();
//
//      if (keys != null) {
//         for (int i = 0 ; i < keys.length ; i++) {
//            props.put(keys[i], img.getProperty(keys[i]));
//         }
//      }
//      BufferedImage ret = new BufferedImage(cm, raster,
//            isAlphaPremultiplied,
//            props);
//      img.copyData(raster);
//
//      return ret;
//   }
}
