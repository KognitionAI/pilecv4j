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

package com.jiminger.image.canny;

import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.util.Map;

import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;
import javax.media.jai.TiledImage;
import javax.media.jai.UntiledOpImage;


/**
 * EdgeDetectorOpImage is an extension of PointOpImage that takes two
 * integer parameters and one source and performs a modified threshold
 * operation on the given source.
 */

@SuppressWarnings("unchecked") // this is a jai issue so there's nothing I can do
public class EdgeDetectorOpImage extends UntiledOpImage
{
   public static byte EDGE = (byte)-1;
   public static byte NOEDGE = (byte)0;
   public static byte POSSIBLE_EDGE = (byte)127;

   private final float highThreshold;
   private final float lowThreshold;
   private final float sigma;
   private final GradientDirectionImageHolder ih;

   public static void setEdgePixVal(final byte edgePixVal, final byte noedgePixVal)
   {
      EDGE = edgePixVal;
      NOEDGE = noedgePixVal;

      while (POSSIBLE_EDGE == EDGE || POSSIBLE_EDGE == NOEDGE)
         POSSIBLE_EDGE++;
   }

   public EdgeDetectorOpImage(final RenderedImage source, final ImageLayout il, final Map<Object,Object> map,
                              final BorderExtender be, final float lowThreshold,
                              final float highThreshold, final float sigma, 
                              final GradientDirectionImageHolder ih)
   {
      super(source,map,il);
      this.highThreshold = highThreshold;
      this.lowThreshold = lowThreshold;
      this.sigma = sigma;
      this.ih=ih;

   }

   protected void computeImage(final Raster araster[], 
                               final WritableRaster writableraster, 
                               final Rectangle rectangle)
   {
      // need to verify that the source image is correct
      final RasterFormatTag arasterformattag[] = getFormatTags();
      final Raster raster = araster[0];
      final Rectangle rectangle1 = mapDestRect(rectangle, 0);
      final RasterAccessor rasteraccessor = 
         new RasterAccessor(raster, rectangle1, 
                            arasterformattag[0], 
                            getSourceImage(0).getColorModel());

      final RasterAccessor destrasteraccessor = 
         new RasterAccessor(writableraster, rectangle, 
                            arasterformattag[1], getColorModel());

//      ArrayList l= new ArrayList();
//      for (int i = 0; i < arasterformattag.length; i++)
//         l.add(arasterformattag[i]);
//      System.out.println("arasterformattag[]:" + l);
//
//      l= new ArrayList();
//      for (int i = 0; i < araster.length; i++)
//         l.add(araster[i]);
//      System.out.println("rasters:" + l);
//
//      System.out.println("raster's data type: " + destrasteraccessor.getDataType());

      doCannyNative(rasteraccessor, destrasteraccessor);
   }

   private static int [] bandstride = { 0 };
   private void doCannyNative(final RasterAccessor srcra, final RasterAccessor destra)
   {
      final int destwidth = destra.getWidth();
      final int destheight = destra.getHeight();
//      int destbandcount = destra.getNumBands();
      final byte bandeddest[][] = destra.getByteDataArrays(); /// contains the data
      final int destBandOffsets[] = destra.getBandOffsets();
      final int destPixelStride = destra.getPixelStride();
      final int destScanlineStride = destra.getScanlineStride();
      final byte bandedsrc[][] = srcra.getByteDataArrays();
      final int srcBandOffsets[] = srcra.getBandOffsets();
      final int srcPixelStride = srcra.getPixelStride();
      final int srcScanLineStride = srcra.getScanlineStride();

//      float [] gradDirImData = (ih != null) ? new float[destwidth * destheight];

      final TiledImage ti = ih != null ? new TiledImage(
         0,0,destwidth, destheight,0,0,
         new PixelInterleavedSampleModel(
            DataBuffer.TYPE_BYTE,destwidth,destheight,1,destwidth, bandstride),
         new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_GRAY),false,false,
            ComponentColorModel.OPAQUE,DataBuffer.TYPE_BYTE)) : null;
      final WritableRaster gradRaster = ti == null ? null : ti.getWritableTile(0,0);
      final DataBufferByte gradDB = gradRaster == null ? null : (DataBufferByte)gradRaster.getDataBuffer();
      final byte [] gradImageData = gradDB == null ? null : gradDB.getData();

      canny(destwidth, destheight, bandeddest[0], // one band
            destBandOffsets[0], destPixelStride,
            destScanlineStride, bandedsrc[0], // one band
            srcBandOffsets[0], srcPixelStride,
            srcScanLineStride, lowThreshold, 
            highThreshold, sigma, NOEDGE,EDGE,
            POSSIBLE_EDGE,gradImageData);

      if (ih != null)
         ih.ti = ti;
//         ih.gradientDir = gradDirImData;
   }
      
   private native void canny(
      int destwidth, int destheight,
      byte [] dest, int destOffset, 
      int destPixelStride, int destScanlineStride,
      byte [] src, int srcOffset,
      int srcPixelStride, int srcScanLineStride,
      float lowThreshold, float highThreshold,
      float sigma, byte noedgeval, byte edgeval, 
      byte possibleEdgeVal, byte [] gradientDirImage);

   public static class GradientDirectionImageHolder
   {
      public TiledImage ti = null;
//      public float[] gradientDir = null;
   }
}
