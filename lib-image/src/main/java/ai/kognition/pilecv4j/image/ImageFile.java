/***********************************************************************
 * Legacy Film to DVD Project
 * Copyright (C) 2005 James F. Carroll
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 ****************************************************************************/

package ai.kognition.pilecv4j.image;

import static org.opencv.imgcodecs.Imgcodecs.IMREAD_UNCHANGED;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.kognition.pilecv4j.image.CvRaster.Closer;

public class ImageFile {
   static {
      CvMat.initOpenCv();
   }

   private static final Logger LOGGER = LoggerFactory.getLogger(ImageFile.class);

   public static BufferedImage readBufferedImageFromFile(final String filename) throws IOException {
      LOGGER.trace("Reading image from {}", filename);
      final File f = new File(filename);
      if(!f.exists())
         throw new FileNotFoundException(filename);
      BufferedImage ret = ImageIO.read(f);
      if(ret == null) {
         LOGGER.info("Failed to read '{}' using ImageIO", filename);
         try (Closer closer = new Closer()) {
            final Mat mat = Imgcodecs.imread(filename, IMREAD_UNCHANGED);
            if(mat == null)
               throw new IllegalArgumentException("Can't read '" + filename + "' as an image. No codec available in either ImageIO or OpenCv");
            if(filename.endsWith(".jp2") && CvType.channels(mat.channels()) > 1)
               Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
            ret = Utils.mat2Img(mat);
         }
      }
      LOGGER.trace("Read {} from {}", ret, filename);
      return ret;
   }

   /**
    * Read a {@link CvMat} from a file. You should make sure this is assigned in a try-with-resource
    * or the CvMat will leak.
    */
   public static CvMat readMatFromFile(final String filename) throws IOException {
      LOGGER.trace("Reading image from {}", filename);
      final File f = new File(filename);
      if(!f.exists())
         throw new FileNotFoundException(filename);

      final CvMat ret;

      try (Closer cx = new Closer()) {
         final Mat mat = Imgcodecs.imread(filename, IMREAD_UNCHANGED);
         if(mat == null) {
            LOGGER.debug("Failed to read '" + filename + "' using OpenCV");
            ret = Utils.img2CvMat(ImageIO.read(f));
         } else {
            if(filename.endsWith(".jp2") && CvType.channels(mat.channels()) > 1)
               Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2BGR);
            ret = CvMat.move(mat);
         }
      }
      LOGGER.trace("Read {} from {}", ret, filename);
      return ret;
   }

   public static BufferedImage convert(final Image im, final int type) {
      if(im instanceof BufferedImage)
         return (BufferedImage)im;
      final BufferedImage bi = new BufferedImage(im.getWidth(null), im.getHeight(null), type);
      final Graphics bg = bi.getGraphics();
      bg.drawImage(im, 0, 0, null);
      bg.dispose();
      return bi;
   }

   public static void writeImageFile(final BufferedImage ri, final String filename) throws IOException {
      if(!doWrite(ri, filename)) {
         LOGGER.debug("Failed to write '" + filename + "' using ImageIO");
         try (CvMat mat = Utils.img2CvMat(ri);) {
        	 if(!doWrite(mat, filename, true))
        		 throw new IllegalArgumentException("Failed to write");
         }
      }
   }

   public static void writeImageFile(final Mat ri, final String filename) throws IOException {
      if(!doWrite(ri, filename, false)) {
         LOGGER.debug("Failed to write '" + filename + "' using OpenCV");
         final BufferedImage bi = Utils.mat2Img(ri);
         if(!doWrite(bi, filename))
            throw new IllegalArgumentException("Failed to write");
      }
   }

   private static boolean doWrite(final BufferedImage ri, final String filename) throws IOException {
      LOGGER.trace("Writing image {} to {}", ri, filename);
      final int dotindex = filename.lastIndexOf(".");
      if(dotindex < 0)
         throw new IOException("No extention on " + filename);
      final String ext = filename.substring(dotindex + 1);

      final File f = new File(filename).getCanonicalFile();
      final File p = f.getParentFile();
      // make sure the output directory exists.
      p.mkdirs();
      final Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix(ext);
      boolean wrote = false;
      while(iter.hasNext()) {
         final ImageWriter writer = iter.next(); // grab the first one
         try (final ImageOutputStream ios = ImageIO.createImageOutputStream(f);) {
            final ImageWriteParam param = writer.getDefaultWriteParam();

            writer.setOutput(ios);

            writer.write(null, new IIOImage(ri, null, null), param);
         }
         wrote = true;
      }
      return wrote;
   }

   private static boolean doWrite(final Mat ri, final String filename, boolean canOverwrite) throws IOException {
      LOGGER.trace("Writing image {} to {}", ri, filename);
      try (final CvMat newMat = new CvMat(); ) {
    	  final Mat toWrite;
    	  if (filename.endsWith(".jp2")) {
    		  toWrite = (canOverwrite) ? ri : newMat;
    		  Imgproc.cvtColor(ri, toWrite, Imgproc.COLOR_BGR2RGB);
    	  } else
    		  toWrite = ri;

    	  return Imgcodecs.imwrite(filename, toWrite);
      }
   }

   private static double scale(final int width, final int height, final ImageDestinationDefinition dest) {
      double scale = -1.0;
      if(dest.maxh != -1) {
         if(height > dest.maxh)
            // see what we need to scale to make the height the same.
            scale = ((double)dest.maxh) / ((double)height);
      }

      if(dest.maxw != -1) {
         final int adjwidth = (scale >= 0.0) ? (int)Math.round(scale * width) : width;
         if(adjwidth > dest.maxw) {
            scale = ((double)dest.maxw) / ((double)adjwidth);
         }
      }

      if(dest.maxe != -1) {
         final int adjedge = width > height ? (scale >= 0.0 ? (int)Math.round(scale * width) : width)
               : (scale >= 0.0 ? (int)Math.round(scale * height) : height);
         if(adjedge > dest.maxe) {
            scale = ((double)(dest.maxe)) / ((double)adjedge);
         }
      }
      return scale;
   }

   public static void transcode(BufferedImage bi, final ImageDestinationDefinition dest) throws IOException {
      if(infile != null && infile.equalsIgnoreCase(dest.outfile))
         throw new IOException("Can't overwrite original file durring transcode (" + infile + ").");

      if(dest.maxw != -1 || dest.maxh != -1 || dest.maxe != -1) {
         final int width = bi.getWidth();
         final int height = bi.getHeight();

         final double scale = scale(width, height, dest);

         if(scale >= 0.0) {

            final int newwidth = (int)Math.round(scale * (width));
            final int newheight = (int)Math.round(scale * (height));

            bi = convert(bi.getScaledInstance(newwidth, newheight, BufferedImage.SCALE_DEFAULT), bi.getType());
         }
      }

      writeImageFile(bi, dest.outfile);
   }

   // public static void transcode(final CvRaster bi, final ImageDestinationDefinition dest) throws IOException {
   // if (infile != null && infile.equalsIgnoreCase(dest.outfile))
   // throw new IOException("Can't overwrite original file durring transcode (" + infile + ").");
   //
   // if (dest.maxw != -1 || dest.maxh != -1 || dest.maxe != -1) {
   // final int width = bi.cols;
   // final int height = bi.rows;
   //
   // double scale = -1.0;
   // if (dest.maxh != -1) {
   // if (height > dest.maxh)
   // // see what we need to scale to make the height the same.
   // scale = ((double) dest.maxh) / ((double) height);
   // }
   //
   // if (dest.maxw != -1) {
   // final int adjwidth = (scale >= 0.0) ? (int) Math.round(scale * width) : width;
   // if (adjwidth > dest.maxw) {
   // scale = ((double) dest.maxw) / ((double) adjwidth);
   // }
   // }
   //
   // if (dest.maxe != -1) {
   // final int adjedge = width > height ? (scale >= 0.0 ? (int) Math.round(scale * width) : width)
   // : (scale >= 0.0 ? (int) Math.round(scale * height) : height);
   // if (adjedge > dest.maxe) {
   // scale = ((double) (dest.maxe)) / ((double) adjedge);
   // }
   // }
   //
   // if (scale >= 0.0) {
   // final int newwidth = (int) Math.round(scale * (width));
   // final int newheight = (int) Math.round(scale * (height));
   //
   // Imgproc.resize(bi.mat, bi.mat, new Size(newwidth, newheight), 0, 0, Imgproc.INTER_LINEAR);
   // }
   // }
   //
   // writeImageFile(bi, dest.outfile);
   // }

   public static class ImageDestinationDefinition {
      public String outfile = null;
      public int maxw = -1;
      public int maxh = -1;
      public int maxe = -1;
      public boolean verify = false;

      public void set() {}
   }

   public static String infile = null;

   public static void main(final String[] args)
         throws IOException {
      final List<ImageDestinationDefinition> dests = commandLine(args);
      if(dests == null || dests.size() == 0) {
         usage();
         return;
      }

      if(infile == null) {
         usage();
         return;
      }

      final BufferedImage image = readBufferedImageFromFile(infile);
      // final CvRaster raster = CvRaster.create(image);
      // final short[] data = (short[]) raster.data;
      // final int width = raster.cols;
      // final short[] pixel = new short[1];
      // raster.apply((ShortPixelSetter) (r, c) -> {
      // final short pix = data[r * width + c];
      // final int pixu = Short.toUnsignedInt(pix);
      // pixel[0] = (short) (((pixu & 0xff00) >>> 8) | ((pixu & 0xff) << 8));
      // return pixel;
      // });

      for(final ImageDestinationDefinition dest: dests) {
         transcode(image, dest);

         if(dest.verify) {
            final RenderedImage im = readBufferedImageFromFile(dest.outfile);
            final int width2 = im.getWidth();
            final int height2 = im.getHeight();

            if(dest.maxw != width2 || dest.maxh != height2 || dest.maxe != ((width2 > height2) ? width2 : height2))
               throw new IOException("Verification failed!");
         }
      }
   }

   static private List<ImageDestinationDefinition> commandLine(final String[] args) {
      final List<ImageDestinationDefinition> ret = new ArrayList<ImageDestinationDefinition>();
      ImageDestinationDefinition cur = null;

      for(int i = 0; i < args.length; i++) {
         final String optionArg = args[i];
         // see if we are asking for help
         if("help".equalsIgnoreCase(optionArg) ||
               "-help".equalsIgnoreCase(optionArg)) {
            usage();
            return null;
         }

         if("-i".equalsIgnoreCase(optionArg)) {
            if(infile != null) {
               System.err.println("One infile only");
               usage();
               return null;
            }
            infile = args[i + 1];
            i++;
         }

         else if("-o".equalsIgnoreCase(args[i])) {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if(cur.outfile != null)
               cur = push(cur, ret);
            cur.outfile = args[i + 1];
            i++;
         } else if("-verify".equalsIgnoreCase(args[i])) {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if(cur.verify == false)
               cur = push(cur, ret);
            cur.verify = true;
         } else if("-maxw".equalsIgnoreCase(args[i])) {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if(cur.maxw != -1)
               cur = push(cur, ret);
            cur.maxw = Integer.parseInt(args[i + 1]);
            i++;
         } else if("-maxh".equalsIgnoreCase(args[i])) {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if(cur.maxh != -1)
               cur = push(cur, ret);
            cur.maxh = Integer.parseInt(args[i + 1]);
            i++;
         } else if("-maxe".equalsIgnoreCase(args[i])) {
            cur = cur == null ? new ImageDestinationDefinition() : cur;
            if(cur.maxe != -1)
               cur = push(cur, ret);
            cur.maxe = Integer.parseInt(args[i + 1]);
            i++;
         } else {
            usage();
            return null;
         }
      }

      if(cur != null) {
         cur.set();
         ret.add(cur);
      }

      return ret;
   }

   static private ImageDestinationDefinition push(final ImageDestinationDefinition cur,
         final List<ImageDestinationDefinition> ret) {
      ret.add(cur);
      cur.set();
      return new ImageDestinationDefinition();
   }

   static private void usage() {
      System.out.println("usage: java [javaargs] ImageFile -i infile -o outfile [-maxw width] [-maxh height] [-maxe maxEdge] [-verify]");
      System.out.println("       options -o through -verify can be repeated to convert an image file");
      System.out.println("       to a number of different formats and dimentions");
   }
}
