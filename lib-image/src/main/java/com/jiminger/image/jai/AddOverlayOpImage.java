package com.jiminger.image.jai;
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


import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.util.Map;

import javax.media.jai.ImageLayout;
import javax.media.jai.NullOpImage;
import javax.media.jai.OpImage;

import com.jiminger.image.canny.EdgeDetectorOpImage;

public class AddOverlayOpImage extends NullOpImage
{
   public static final byte ROVERLAY = (byte)100;
   public static final byte GOVERLAY = (byte)101;
   public static final byte BOVERLAY = (byte)102;
   public static final byte YOVERLAY = (byte)103;
   public static final byte COVERLAY = (byte)104;
   public static final byte MOVERLAY = (byte)105;
   public static final byte OOVERLAY = (byte)106;
   public static final byte GRAYOVERLAY = (byte)107;


   public AddOverlayOpImage(RenderedImage source, ImageLayout il, Map<?,?> map)
   {
      super(source,addOverlayImageLayout(il == null ? new ImageLayout(source) : il),map,
            OpImage.OP_COMPUTE_BOUND);
   }


   private static ImageLayout addOverlayImageLayout(ImageLayout il)
   {
      byte [] r = new byte[256];
      byte [] g = new byte[256];
      byte [] b = new byte[256];

      r[intify(EdgeDetectorOpImage.EDGE)] = 
         g[intify(EdgeDetectorOpImage.EDGE)] = 
         b[intify(EdgeDetectorOpImage.EDGE)] = -1;
      r[intify(ROVERLAY)] = -1;
      g[intify(GOVERLAY)] = -1;
      b[intify(BOVERLAY)] = -1;

      il.setColorModel(
         new IndexColorModel(8,256,r,g,b));

      return il;
   }

   private static int intify(byte b)
   {
      return (b < 0) ? ((int)b) + 256 : (int)b;
   }
}
