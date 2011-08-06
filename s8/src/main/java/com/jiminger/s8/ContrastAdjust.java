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

import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.media.jai.JAI;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;
import javax.media.jai.TileCache;
import javax.media.jai.TiledImage;

import com.jiminger.nr.MinimizerException;
import com.jiminger.nr.Minimizer;
import com.jiminger.util.CommandLineParser;
import com.jiminger.util.LibraryLoader;
import com.sun.media.jai.codec.FileSeekableStream;


public class ContrastAdjust
{
   public static final long megaBytes = 1024L * 1024L;
   public static final long defaultTileCacheSize = 300;
   public static long tileCacheSize = defaultTileCacheSize * megaBytes;
   public static String sourceFileName = null;
   public static String destFileName = null;
   public static double[] finalX = null;
   public static String outfileType = "BMP";

   public static PixelMapper mapperToUse = null;

   public static void main(String [] args)
      throws MinimizerException
   {
      // First parse the command line and test for various
      //  settings.
      if (!commandLine(args))
         return;

      // Set the tile cache up on JAI
      TileCache tc = JAI.createTileCache(tileCacheSize);
      JAI jai = JAI.getDefaultInstance();
      jai.setTileCache(tc);

      contrastAdjust(sourceFileName,destFileName,outfileType,finalX,mapperToUse);
   }

   public static void contrastAdjust(String infile, String outFile, 
                                     String outfileType, double [] finalX)
      throws MinimizerException
   {
      contrastAdjust(infile,outFile,outfileType,finalX,null);
   }

   public static void contrastAdjust(String infile, String outFile, 
                                     String outfileType, double [] finalX,
                                     PixelMapper mapper)
      throws MinimizerException
   {
      /*
       * Create an input stream from the specified file name
       * to be used with the file decoding operator.
       */
      FileSeekableStream stream = null;
      try {
         stream = new FileSeekableStream(infile);
      } catch (IOException e) {
         e.printStackTrace();
         System.exit(0);
      }

      /* Create an operator to decode the image file. */
      RenderedImage origImage = JAI.create("stream", stream);

      TiledImage mapped = contrastAdjust(origImage,finalX, mapper);

      JAI.create("filestore",mapped,outFile, outfileType, null);
   }

   public static TiledImage contrastAdjust(RenderedImage origImage, double [] finalX)
      throws MinimizerException
   {
      return contrastAdjust(origImage,finalX,null);
   }

   public static TiledImage contrastAdjust(RenderedImage origImage, double [] finalX,
                                           PixelMapper mapper)
      throws MinimizerException
   {
      if (mapper == null)
         mapper = new DefaultMapper();

      MapFit fit;
      if (finalX == null)
      {
         int [][] histogram = histogram(origImage);
         int [] normMap = normalizeMap(histogram[histogram.length - 1]);

//         fit = new MapFit(normMap);
//         Minimizer min = new Minimizer(fit);
//         double [] x = new double[3];
//         x[0] = 0.0; x[1] = 255.0; x[2] = 0.0;
//         min.minimize(x);
//         finalX = min.getFinalPostion();
//         int [] fitMap = fit.generateMap(finalX);
//         mapper.setLookup(fitMap);
         mapper.setLookup(normMap);
      }
      else
      {
         fit = new MapFit(256);
         int [] fitMap = fit.generateMap(finalX);
         mapper.setLookup(fitMap);
      }

      return applyMap(origImage,mapper);
   }

   private static TiledImage applyMap(RenderedImage image, PixelMapper pixelMap)
   {
      Raster srcraster = image.getData();
      int width = srcraster.getWidth();
      int height = srcraster.getHeight();
      int bandcount = srcraster.getNumBands();

      TiledImage ret = new TiledImage(
         0,0,width,height,0,0,
         image.getSampleModel().createCompatibleSampleModel(width,height),
         image.getColorModel());

      RenderedImage [] srcs = new RenderedImage[1];
      srcs[0] = image;
      RasterFormatTag[] tags = RasterAccessor.findCompatibleTags(srcs,ret);
      RasterFormatTag srctag = tags[0];
      RasterFormatTag dsttag = tags[1];

      RasterAccessor srcra = 
         new RasterAccessor(srcraster, srcraster.getBounds(), srctag, image.getColorModel());

      Raster dstraster = ret.getWritableTile(0,0);
      RasterAccessor dstra = 
         new RasterAccessor(dstraster, dstraster.getBounds(), dsttag, ret.getColorModel());

      byte bandedsrc[][] = srcra.getByteDataArrays();
      int srcBandOffsets[] = srcra.getBandOffsets();
      int srcPixelStride = srcra.getPixelStride();
      int srcScanLineStride = srcra.getScanlineStride();

      byte bandeddst[][] = dstra.getByteDataArrays();
      int dstBandOffsets[] = dstra.getBandOffsets();
      int dstPixelStride = dstra.getPixelStride();
      int dstScanLineStride = dstra.getScanlineStride();

      int pixel[] = new int[bandcount];
      int newpixel[] = new int[bandcount];

      for(int row = 0; row < height; row++)
      {
         int srcrowstride = srcScanLineStride * row;
         int dstrowstride = dstScanLineStride * row;

         for (int col = 0; col < width; col++)
         {
            int srccolstride = srcPixelStride * col;
            int srctotalStride = srcrowstride + srccolstride;
            int dstcolstride = dstPixelStride * col;
            int dsttotalStride = dstrowstride + dstcolstride;

            for(int band = 0; band < bandcount; band++)
            {
               byte src[] = bandedsrc[band];
               int srcpos = srcBandOffsets[band] + srctotalStride;
               pixel[band] = intify(src[srcpos]);
            }

            pixelMap.map(pixel,newpixel);

            for(int band = 0; band < bandcount; band++)
            {
               byte dst[] = bandeddst[band];
               int dstpos = dstBandOffsets[band] + dsttotalStride;
               dst[dstpos] = byteify(newpixel[band]);
            }
         }
      }

      return ret;
   }

   public static interface PixelMapper
   {
      public void setLookup(int [] lookup);
      public void map(int [] pixel, int [] newpixel);
   }

   //RGB-to-YIQ 
   //
   //Y = .299R + .587G + .114B 
   //I = .596R - .274G - .322B 
   //Q = .211R - .523G - .312B 
   //
   //YIQ-to-RGB 
   //
   //R = -1.129Y + 3.306I - 3.000Q 
   //G =  1.607Y -  .934I +  .386Q 
   //B =  3.458Y - 3.817I + 5.881Q 
   public static class YIQMapper implements PixelMapper
   {

      int [] map;
      static private final int R = 0;
      static private final int G = 1;
      static private final int B = 2;

      public void setLookup(int [] map)
      {
         this.map = map;
      }

      public void map(int [] pixel, int [] newpixel)
      {
         // translate to YIQ
         double r = (double)pixel[R];
         double g = (double)pixel[G];
         double b = (double)pixel[B];

         double y = (0.299 * r) + (0.587 * g) + (0.114 * b);
         double i = (0.596 * r) - (0.247 * g) - (0.322 * b);
         double q = (0.211 * r) - (0.523 * g) + (0.312 * b);

         // now map Y
         y = map[(int)(y + 0.5)];
         int newval = (int)((1.0 * y) + (0.956 * i) + (0.621 * q) + 0.5);
         if (newval > 255) newval = 255;
         if (newval < 0) newval = 0;
         newpixel[R] = newval;
         newval = (int)((1.0 * y) - (0.272 * i) - (0.647 * q) + 0.5);
         if (newval > 255) newval = 255;
         if (newval < 0) newval = 0;
         newpixel[G] = newval;
         newval = (int)((1.0 * y) - (1.105 * i) + (1.702 * q) + 0.5);
         if (newval > 255) newval = 255;
         if (newval < 0) newval = 0;
         newpixel[B] = newval;
      }
   }

   public static class HSIMapper implements PixelMapper
   {

      int [] map;
      static private final int R = 0;
      static private final int G = 1;
      static private final int B = 2;

      public void setLookup(int [] map)
      {
         this.map = map;
      }

      private double min(double x, double y, double z)
      {
         return (x < y ? (x < z ? x : z) : ( y < z ? y : z));
      }

      public void map(int [] pixel, int [] newpixel)
      {
         // translate to YIQ
         double r = (double)pixel[R] / ((double)map.length - 1);
         double g = (double)pixel[G] / ((double)map.length - 1);
         double b = (double)pixel[B] / ((double)map.length - 1);

         double ni = ((double)(pixel[R] + pixel[G] + pixel[B]))/ 3.0;
         double i = ni / ((double)map.length - 1);
         double s = 1.0 - (min(r,g,b) / i);
         double rmg = r - g;
         double h = Math.acos(
            (rmg + (r - b)) / (2.0 * Math.sqrt((rmg * rmg) + ((r - b) * (g - b)))));

         if (b > g)
            h = (2.0 * Math.PI) - h;

         // now map i
         ni = (double)map[(int)ni];
         i = ni / ((double)map.length - 1);

         if (h < (2.0 * Math.PI / 3.0))
         {
            b = (1.0 - s) / 3.0;
            r = (1.0 + ( ( s * Math.cos(h) / Math.cos ((Math.PI/3.0) - h)))) / 3.0;
            g = 1.0 - (r + b);
         }
         else if (h < (4.0 * Math.PI / 3.0))
         {
            h = h - (2.0 * Math.PI / 3.0);
            g = (1.0 + ( ( s * Math.cos(h) / Math.cos ((Math.PI/3.0) - h)))) / 3.0;
            r = ( 1.0 - s )/ 3.0;
            b = 1.0 - (r + g);
         }
         else
         {
            h = h - ((4.0 * Math.PI) / 3.0);
            b = (1.0 + ( ( s * Math.cos(h) / Math.cos ((Math.PI/3.0) - h)))) / 3.0;
            g = ( 1.0 - s )/ 3.0;
            r = 1.0 - (b + g);
         }

         int newval = (int)((3.0 * i * r) * (double)map.length);
         if (newval > 255) newval = 255;
         if (newval < 0) newval = 0;
         newpixel[R] = newval;
         newval = (int)((3.0 * i * g) * (double)map.length);
         if (newval > 255) newval = 255;
         if (newval < 0) newval = 0;
         newpixel[G] = newval;
         newval = (int)((3.0 * i * b) * (double)map.length);
         if (newval > 255) newval = 255;
         if (newval < 0) newval = 0;
         newpixel[B] = newval;
      }
   }

   public static class DefaultMapper implements PixelMapper
   {
      double [] percentMap;

      public void setLookup(int [] map)
      {
         percentMap = new double[map.length];
         for (int i = 0; i < map.length; i++)
            percentMap[i] = ((double)map[i])/((double)i);
      }

      public void map(int [] pixel, int [] newpixel)
      {
         int averagePixValue = 0;
         for (int i = 0; i < pixel.length; i++)
            averagePixValue += pixel[i];

         averagePixValue = averagePixValue / pixel.length;
         double mult = percentMap[averagePixValue];

         for (int i = 0; i < pixel.length; i++)
         {
            int newPixVal = (int)((mult * ((double)pixel[i])) + 0.5);
            if (newPixVal > (percentMap.length - 1)) 
               newPixVal = percentMap.length - 1;
            newpixel[i] = newPixVal;
         }
      }
   }

   private static int [] normalizeMap(int [] hist)
   {
      // assuming the following results in a zeroed
      //  array
      int [] ret = new int[hist.length];
      double maxVal = (double)(ret.length - 1);
      double [] accum = new double[ret.length];

      int totalCount = 0;
      for (int i = 0; i < hist.length; i++)
      {
         totalCount += hist[i];
         accum[i] = (double)totalCount;
      }

      // we need to squeeze totalCount down to 
      //  maxVal
      for (int i = 0; i < accum.length; i++)
      {
         ret[i] = (int)(((accum[i] * maxVal) / (double)totalCount) + 0.5);
         if (ret[i] > ret.length)
            ret[i] = ret.length;
      }

      return ret;
   }

   private static int [][] histogram(RenderedImage image)
   {
      RenderedImage [] srcs = new RenderedImage[1];
      srcs[0] = image;
      RasterFormatTag[] tags = RasterAccessor.findCompatibleTags(srcs,image);
      RasterFormatTag srctag = tags[0];
//      RasterFormatTag dsttag = tags[1];

      Raster srcraster = image.getData();
      RasterAccessor srcra = 
         new RasterAccessor(srcraster, srcraster.getBounds(), srctag, image.getColorModel());

      int srcwidth = srcra.getWidth();
      int srcheight = srcra.getHeight();
      int srcbandcount = srcra.getNumBands();
      byte bandedsrc[][] = srcra.getByteDataArrays();
      int srcBandOffsets[] = srcra.getBandOffsets();
      int srcPixelStride = srcra.getPixelStride();
      int srcScanLineStride = srcra.getScanlineStride();

      int [][] histogram = new int[srcbandcount + 1][];
      for (int i = 0; i < histogram.length; i++)
      {
         histogram[i] = new int[256];
         for (int j = 0; j < 256; j++)
            histogram[i][j] = 0;
      }

      for(int srcRow = 0; srcRow < srcheight; srcRow++)
      {
         int rowstride = srcScanLineStride * srcRow;

         for (int srcCol = 0; srcCol < srcwidth; srcCol++)
         {
            int colstride = srcPixelStride * srcCol;
            int totalStride = rowstride + colstride;

            int averagePixValue = 0;
            for(int band = 0; band < srcbandcount; band++)
            {
               byte src[] = bandedsrc[band];
               int srcpos = srcBandOffsets[band] + totalStride;
               int pixval = intify(src[srcpos]);
               histogram[band][pixval]++;
               averagePixValue += pixval;
            }

            averagePixValue = averagePixValue / srcbandcount;
            histogram[histogram.length - 1][averagePixValue]++;
         }
      }

      return histogram;
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

      destFileName = cl.getProperty("o");
      if (destFileName == null)
      {
         usage();
         return false;
      }

      String tmps = cl.getProperty("cs");
      if (tmps != null)
      {
         tileCacheSize = Long.parseLong(tmps) * megaBytes;
         System.out.println("   using a tile cache size of " + tileCacheSize);
      }

      tmps = cl.getProperty("x");
      if (tmps != null)
      {
         StringTokenizer stok = new StringTokenizer(tmps,",");
         if (stok.countTokens() != 3)
         {
            usage();
            return false;
         }

         finalX = new double[3];
         for (int i = 0; stok.hasMoreTokens(); i++)
            finalX[i] = Double.parseDouble(stok.nextToken());

         finalX[2] = Math.log(1.0/finalX[2]);
      }

      tmps = cl.getProperty("mapper");
      if (tmps != null)
      {
         if ("yiq".equalsIgnoreCase(tmps))
            mapperToUse = new YIQMapper();
         else if ("hsi".equalsIgnoreCase(tmps))
            mapperToUse = new HSIMapper();
         else if ("default".equalsIgnoreCase(tmps))
            mapperToUse = new DefaultMapper();
         else
         {
            System.out.println("Mapper possibiilies currently include \"yiq\", \"hsi\", or \"default\".");
            return false;
         }
      }

      return true;
   }

   private static void usage()
   {
      System.out.println(
         "usage: java [javaargs] ContrastAdjust -f filename -o outfilename [-cs " + defaultTileCacheSize + "] [-x x0,x1,x2] [-mapper yiq|hsi|default]");
      System.out.println();
      System.out.println("  -f this is how you supply filename of the source image.");
      System.out.println("  -cs image tile cache size in mega bytes.");
      System.out.println("  -x high low and factor for contrast stretch");
      System.out.println("  -mapper will apply the contrast to the intensity in the specified colorspace.");
   }

   private static int intify(byte b)
   {
      return (b < 0) ? ((int)b) + 256 : (int)b;
   }

   private static byte byteify(int i)
   {
      return i > 127 ? (byte)(i - 256) : (byte)i;
   }

   public static class MapFit implements Minimizer.Func
   {
      int [] map;
      int size;

      public MapFit(int size)
      {
         this.map = null;
         this.size = size;
      }

      public MapFit(int [] map)
      {
         this.map = map;
         this.size = map.length;
      }

      public double func(double [] x)
      {
         double x0 = x[0];
         double x1 = x[1];

         double ret = 0.0;
         double tmpd;

         if (x0 < 0.0)
            ret += (x0 * x0 * x0 * x0);

         if (x1 > (map.length - 1))
         {
            tmpd = x1 - (double)(map.length - 1);
            ret += tmpd * tmpd * tmpd * tmpd;
         }

         for (int i = 0; i < map.length; i++)
         {
            tmpd = mapEntry(x,i) - (double)map[i];
            ret += (tmpd * tmpd);
         }

         return ret;
      }

      public double mapEntry(double [] x, int i)
      {
         double x0 = x[0];
         double x1 = x[1];
         double n = Math.exp(x[2]);

         if (i < x0)
            return 0.0;
         if (i > x1)
            return (double)(size - 1);

         return (Math.pow((((double)i - x0)/(x1 - x0)),n)) * (double)(size - 1);
      }

      public int[] generateMap(double [] x)
      {
         int [] ret = new int[size];

         for (int i = 0; i < size; i++)
         {
            ret[i] = (int)(mapEntry(x,i) + 0.5);

            if (ret[i] > (size - 1))
               ret[i] = size - 1;
         }

         return ret;
      }
   }

   static {
      LibraryLoader.loadLibrary("s8");
   }

   static public double [] remap(double [] x)
   {
      double [] ret= new double[x.length];
      for (int i = 0; i < ret.length; i++)
         ret[i] = x[i];
      ret[2] = Math.exp(ret[2]);
      return ret;
   }

   static public void printArray(int [][] array, String n)
   {
      for (int i = 0; i < array.length; i++)
      {
         int [] bandogram = array[i];
         for (int j = 0; j < bandogram.length; j++)
            System.out.println(n + "[" + i + "][" + j + "]=" + array[i][j]);
      }
   }

   static public void printArray(int [] array, String n)
   {
      for (int i = 0; i < array.length; i++)
         System.out.println(n + "[" + i + "]=" + array[i]);
   }

   static public void printArray(double [] array, String n)
   {
      for (int i = 0; i < array.length; i++)
         System.out.println(n + "[" + i + "]=" + array[i]);
   }
}

