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


import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.util.Map;

import javax.media.jai.ImageLayout;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;
import javax.media.jai.SourcelessOpImage;

import com.jiminger.image.canny.EdgeDetectorOpImage;
import com.sun.media.jai.opimage.RIFUtil;

/**
 * A single class that is both an OperationDescriptor and
 * a RenderedImageFactory along with the one OpImage it is
 * capable of creating.  The operation implemented is a variation
 * on threshold, although the code may be used as a template for
 * a variety of other point operations.
 *
 */

public class BlankImageDescriptor extends OperationDescriptorImpl 
   implements RenderedImageFactory 
{

   /**
     * 
     */

    private static final long serialVersionUID = 7444334004395018569L;

    public static final int superEightFilmType = 1;
    public static final int eightMMFilmType = 2;

    /** Constructor. */
    public BlankImageDescriptor() {
        super(resources, supportedModes, 0, paramNames, paramClasses, 
	      paramDefaults, null);
    }

    /**
     * The resource strings that provide the general documentation and
     * specify the parameter list for the "BlankImage" operation.
     */
    private static final String[][] resources = {
        {"GlobalName",  "blankImage"},
        {"LocalName",   "blankImage"},
        {"Vendor",      "com.mycompany"},
        {"Description", "A sample operation that thresholds source pixels"},
        {"DocURL",      "http://www.mycompany.com/BlankImageDescriptor.html"},
        {"Version",     "1.0"},
        {"arg0Desc",    "width"},
        {"arg1Desc",    "height"},
        {"arg2Desc",    "defaultValue"}
    };

    /** 
     *  The parameter names for the "BlankImage" operation. Extenders may
     *  want to rename them to something more meaningful. 
     */


    private static final String[] paramNames = {
       "width", "height", "defaultValue"
    };
 
    /** 
     *  The class types for the parameters of the "BlankImage" operation.  
     *  User defined classes can be used here as long as the fully 
     *  qualified name is used and the classes can be loaded.
     */


    private static final Class<?>[] paramClasses = {
       java.lang.Integer.class, java.lang.Integer.class, java.lang.Integer.class
    };
 
    /**
     * The default parameter values for the "BlankImage" operation
     * when using a ParameterBlockJAI.
     */


    private static final Object[] paramDefaults = {
       new java.lang.Integer(512), new java.lang.Integer(512), new java.lang.Integer(0)
    };

    /**
     * The supported modes.
     */
    private static final String[] supportedModes = {
	"rendered"
    };

    /** 
     *  Creates a BlankImageOpImage with the given ParameterBlock if the 
     *  BlankImageOpImage can handle the particular ParameterBlock.
     */


    public RenderedImage create(ParameterBlock paramBlock,
                                RenderingHints renderHints) {
        if (!validateParameters(paramBlock)) {
            return null;
        }

        ImageLayout imagelayout = RIFUtil.getImageLayoutHint(renderHints);
        int w = paramBlock.getIntParameter(0);
        int h = paramBlock.getIntParameter(1);
        int defaultValue = paramBlock.getIntParameter(2);

        return new BlankImageOpImage(imagelayout,renderHints,w,h,defaultValue);
    }

    /**
     *  Checks that all parameters in the ParameterBlock have the 
     *  correct type before constructing the BlankImageOpImage
     */
   public boolean validateParameters(ParameterBlock paramBlock)
   {
      return true;
   }
}


/**
 * BlankImageOpImage is an extension of PointOpImage that takes two
 * integer parameters and one source and performs a modified threshold
 * operation on the given source.
 */

@SuppressWarnings("unchecked")
class BlankImageOpImage extends SourcelessOpImage
{
   private static byte defaultValue;
   private static int [] bandstride = { 0 };

   public BlankImageOpImage(ImageLayout il, Map<?,?> map, 
                            int w, int h, int defaultValue)
   {
      super(blankImageImageLayout(il == null ? null : il,w,h),
            map,
            new PixelInterleavedSampleModel(
               DataBuffer.TYPE_BYTE,w,h,1,w, bandstride),
            0,0,w,h);

      BlankImageOpImage.defaultValue = byteify(defaultValue);
   }

   protected void computeRect(PlanarImage[] pi, WritableRaster dest, Rectangle rect)
   {
      RasterFormatTag rft[] = getFormatTags();
      RasterAccessor destra = new RasterAccessor(dest,rect,rft[0],getColorModel());
      byte [] b = destra.getByteDataArray(0);
      for (int i = 0; i < b.length; i++)
         b[i] = defaultValue;
   }

   private static ImageLayout blankImageImageLayout(ImageLayout il, int w, int h)
   {
      if (il == null)
         il = new ImageLayout(0,0,w,h);

      byte [] r = new byte[256];
      byte [] g = new byte[256];
      byte [] b = new byte[256];

      r[intify(EdgeDetectorOpImage.EDGE)] = 
         g[intify(EdgeDetectorOpImage.EDGE)] = 
         b[intify(EdgeDetectorOpImage.EDGE)] = -1;

      r[intify(AddOverlayOpImage.ROVERLAY)] = -1;
      g[intify(AddOverlayOpImage.GOVERLAY)] = -1;
      b[intify(AddOverlayOpImage.BOVERLAY)] = -1;

      il.setColorModel(
         new IndexColorModel(8,256,r,g,b));

      return il;
   }

   private static int intify(byte b)
   {
      return (b < 0) ? ((int)b) + 256 : (int)b;
   }

   private static byte byteify(int i)
   {
      return i > 127 ? (byte)(i - 256) : (byte)i;
   }
}
