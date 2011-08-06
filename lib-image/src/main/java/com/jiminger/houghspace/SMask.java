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


package com.jiminger.houghspace;

import javax.media.jai.*;
import java.awt.image.*;
import java.awt.color.*;

public class SMask
{
   /**
    * Instantiate a mask of the given dimentions assuming 
    *  that the reference point is the center of the mask.
    */
   public SMask(int mwidth, int mheight)
   {
      // mwidth and mheight need to be odd 
      //  so that the center falls exactly
      //  on a pixel.
      mwidth += (((mwidth & 0x01) == 0) ? 1 : 0);
      mheight += (((mheight & 0x01) == 0) ? 1 : 0);

      this.mwidth = mwidth;
      this.mheight = mheight;

      this.mask = new short[mwidth * mheight];

      this.maskcr = (this.mheight + 1)/2 - 1;
      this.maskcc = (this.mwidth + 1)/2 - 1;
   }

   /**
    * This instantiates a mask with a reference point not
    *  centered. This contructor will not adjust the height
    *  and width that are passed in since the reference 
    *  point is not required to be the center.
    */
   public SMask(int mwidth, int mheight, int refr, int refc)
   {
      this.mwidth = mwidth;
      this.mheight = mheight;
      this.mask = new short[mwidth * mheight];
      this.maskcr = refr;
      this.maskcc = refc;
   }

   /**
    * Set the value of the mask at a location to 
    *  the given value. The value should be either
    *  EDGE or NOEDGE. Entries in the mask are 
    * accessed by row and column (not x,y).
    */
   public void set(int r, int c, short v)
   {
      mask[ (r * mwidth) + c ] = v;
   }

   /**
    * Get the value of the mask at a location 
    *  The return value should be either
    *  EDGE or NOEDGE. Entries in the mask are 
    * accessed by row and column (not x,y).
    */
   public short get(int r, int c)
   {
      return mask[ (r * mwidth) + c ];
   }

   /**
    * Generate a tiled image that contains a view of the mask.
    */
   public TiledImage getMaskImage()
   {
      TiledImage ti = new TiledImage(
         0,0,mwidth,mheight,0,0,
         new PixelInterleavedSampleModel(
            DataBuffer.TYPE_BYTE,mwidth,mheight,1,mwidth, Transform.bandstride),
         new ComponentColorModel(
            ColorSpace.getInstance(ColorSpace.CS_GRAY),false,false,
            ComponentColorModel.OPAQUE,DataBuffer.TYPE_BYTE));

      WritableRaster gradRaster = ti.getWritableTile(0,0);
      DataBufferByte gradDB = (DataBufferByte)gradRaster.getDataBuffer();
      byte [] gradImageData = gradDB.getData();

      int maskwh = mwidth * mheight;
      for (int i = 0; i < maskwh; i++)
         gradImageData[i] = (byte)mask[i];

      return ti;
   }

   public int mwidth;
   public int mheight;
   public int maskcr;
   public int maskcc;
   public short [] mask;

   public static SMask generateGradientMask(Model m, double w, double h, double quantFactor)
   {
      SMask gradDirMask = new SMask((int)((w/quantFactor) + 1.5),(int)((h/quantFactor) + 1.5));

      // now set the mask by sweeping the center
      double x0 = (double)gradDirMask.maskcc; // x0,y0 is the
      double y0 = (double)gradDirMask.maskcr; //  origin of
                                              //  the mask

      for (int r = 0; r < gradDirMask.mheight; r++)
      {
         for (int c = 0; c < gradDirMask.mwidth; c++)
         {
            // is the point r,c a possible 
            //  center if the center of the
            //  mask is the point in question.

            // to figure this out, translate
            //  r,c to the center.
            // but first, find out what r,c is
            //  in the coordinate system of the
            //  mask with the origin centerd.
            double y1 = (double)(gradDirMask.mheight - r - 1) - y0;
            double x1 = ((double)c) - x0;

            // now, if x1,y1 is the center
            //  of the sprocket hole, will
            //  the origin be on the sprocket?
            // That means we need to check 
            //  -x1,-y1 since that is where
            //  the origin will be pushed to
            //  upon translating x1,y1 to the
            //  origin.

            gradDirMask.set(r,c,m.gradient(-x1 * quantFactor,-y1 * quantFactor));
         }
      }

      return gradDirMask;
   }

}

