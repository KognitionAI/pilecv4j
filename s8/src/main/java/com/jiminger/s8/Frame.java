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



import java.awt.Point;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.Properties;

import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;
import javax.media.jai.TiledImage;

import org.opencv.core.Mat;

import com.jiminger.houghspace.Transform;
import com.jiminger.image.PolarLineFit;

@SuppressWarnings("restriction")
public class Frame
{
   public static final double worstEdgeStdDevAllowed = 1.0;

   private Transform.Fit fit;
   private double centerFrameToCenterSprocketXmm;
   private double centerSprocketToFarEdgemm;
//   private int filmLayout;
   private int filmType;
   private FilmEdge sprocketEdge;
   private FilmEdge farEdge;
   private boolean outOfBounds;
   private boolean noCut = true;
   private boolean frameCut = false;
   public int leftmostCol = 99999999;
   public int rightmostCol = -1;
   public int topmostRow = 99999999;
   public int bottommostRow = -1;

   public Frame(Transform.Fit fit, FilmEdge sprocketEdge, FilmEdge farEdge/*, int filmLayout*/, int filmType)
   {
      this.fit = fit;
//      this.filmLayout = filmLayout;
      this.filmType = filmType;

      double [] spec = FilmSpec.filmModel(filmType);

      centerFrameToCenterSprocketXmm = 
         spec[FilmSpec.edgeToFrameCenterIndex] -  // from the edge to the center of the frame
         (spec[FilmSpec.edgeToEdgeIndex] +        // from the same edge to the edge of the sprocket
          spec[FilmSpec.widthIndex]/2.0);         //  + 1/2 of the sprocket width.

      centerSprocketToFarEdgemm = 
         spec[FilmSpec.filmWidthIndex] -          // film width
         (spec[FilmSpec.edgeToEdgeIndex] +        // from the sprocket film edge edge to the edge of the sprocket
          spec[FilmSpec.widthIndex]/2.0);         //  + 1/2 of the sprocket width.

      this.sprocketEdge = sprocketEdge;
      this.farEdge = farEdge;

   }

   public TiledImage cutFrame(RenderedImage image, double resdpi,
                              int frameWidthPix, int frameHeightPix, 
                              boolean reverseImage, int frameNum, 
                              double scaleMult, boolean rescale, 
                              boolean correctrotation)
   {
      noCut = false;
      if (farEdge == null || farEdge.stdDev > worstEdgeStdDevAllowed)
      {
         System.out.println("WARNING: far film edge for frame " + frameNum + " has a stdDev of " + 
                            (farEdge == null ? "null" : Double.toString(farEdge.stdDev)) + 
                            " and will not be used.");

         if (sprocketEdge == null || sprocketEdge.stdDev > worstEdgeStdDevAllowed)
         {
            System.out.println("WARNING: near film edge for frame " + frameNum + " has a stdDev of " + 
                               (sprocketEdge == null ? "null" : Double.toString(sprocketEdge.stdDev)) + 
                               " and will not be used.");
            noCut = true;
            return null;
         }
         else
            preMapSetupUsingSprocketEdge(frameWidthPix,frameHeightPix,scaleMult,reverseImage,rescale,correctrotation);
      }
      else
         preMapSetupUsingFarEdge(frameWidthPix,frameHeightPix,scaleMult,reverseImage,rescale,correctrotation);

      outOfBounds = false;

//      double resdpmm = resdpi / FilmSpec.mmPerInch;
//      double [] spec = FilmSpec.filmModel(filmType);

      TiledImage ret = new TiledImage(
         0,0,frameWidthPix,frameHeightPix,0,0,
         image.getSampleModel().createCompatibleSampleModel(frameWidthPix,frameHeightPix),
         image.getColorModel());

      RenderedImage [] srcs = new RenderedImage[1];
      srcs[0] = image;
      RasterFormatTag[] tags = RasterAccessor.findCompatibleTags(srcs,ret);
      RasterFormatTag srctag = tags[0];
      RasterFormatTag dsttag = tags[1];

      Raster srcraster = image.getData();
      RasterAccessor srcra = 
         new RasterAccessor(srcraster, srcraster.getBounds(), srctag, image.getColorModel());

      Raster dstraster = ret.getWritableTile(0,0);
      RasterAccessor dstra = 
         new RasterAccessor(dstraster, dstraster.getBounds(), dsttag, ret.getColorModel());

      int srcwidth = srcra.getWidth();
      int srcheight = srcra.getHeight();
//      int srcbandcount = srcra.getNumBands();
      byte bandedsrc[][] = srcra.getByteDataArrays();
      int srcBandOffsets[] = srcra.getBandOffsets();
      int srcPixelStride = srcra.getPixelStride();
      int srcScanLineStride = srcra.getScanlineStride();

      int dstwidth = dstra.getWidth();
      int dstheight = dstra.getHeight();
      int dstbandcount = dstra.getNumBands();
      byte bandeddst[][] = dstra.getByteDataArrays();
      int dstBandOffsets[] = dstra.getBandOffsets();
      int dstPixelStride = dstra.getPixelStride();
      int dstScanLineStride = dstra.getScanlineStride();

      for(int band = 0; band < dstbandcount; band++)
      {
         byte dst[] = bandeddst[band];
         byte src[] = bandedsrc[band];
         int srcBegCurBand = srcBandOffsets[band];
         int dstBegCurRow = dstBandOffsets[band];
         for(int dstRow = 0; dstRow < dstheight; dstRow++)
         {
            int dstpos = dstBegCurRow;

            for(int dstCol = 0; dstCol < dstwidth; dstCol++)
            {
               Point srclocation = map(dstRow,dstCol,frameWidthPix,frameHeightPix,reverseImage);

               if (leftmostCol > srclocation.x) leftmostCol = srclocation.x;
               if (rightmostCol < srclocation.x) rightmostCol = srclocation.x;
               if (topmostRow > srclocation.y) topmostRow = srclocation.y;
               if (bottommostRow < srclocation.y) bottommostRow = srclocation.y;

               if (srclocation.y < 0 || srclocation.y >= srcheight || 
                   srclocation.x < 0 || srclocation.x >= srcwidth)
               {
                  dst[dstpos] = (byte)0;
                  outOfBounds = true;
               }
               else
                  dst[dstpos] = src[srcBegCurBand + 
                                    ((srclocation.y * srcScanLineStride) + 
                                     (srclocation.x * srcPixelStride))];

               dstpos += dstPixelStride;
            }

            dstBegCurRow += dstScanLineStride;
         }
      }

      frameCut = true;
      return ret;
   }
   
   public Mat cutFrame(Mat image, double resdpi,
		   int frameWidthPix, int frameHeightPix, 
		   boolean reverseImage, int frameNum, 
		   double scaleMult, boolean rescale, 
		   boolean correctrotation)
   {
	   noCut = false;
	   if (farEdge == null || farEdge.stdDev > worstEdgeStdDevAllowed)
	   {
		   System.out.println("WARNING: far film edge for frame " + frameNum + " has a stdDev of " + 
				   (farEdge == null ? "null" : Double.toString(farEdge.stdDev)) + 
				   " and will not be used.");

		   if (sprocketEdge == null || sprocketEdge.stdDev > worstEdgeStdDevAllowed)
		   {
			   System.out.println("WARNING: near film edge for frame " + frameNum + " has a stdDev of " + 
					   (sprocketEdge == null ? "null" : Double.toString(sprocketEdge.stdDev)) + 
					   " and will not be used.");
			   noCut = true;
			   return null;
		   }
		   else
			   preMapSetupUsingSprocketEdge(frameWidthPix,frameHeightPix,scaleMult,reverseImage,rescale,correctrotation);
	   }
	   else
		   preMapSetupUsingFarEdge(frameWidthPix,frameHeightPix,scaleMult,reverseImage,rescale,correctrotation);

	   outOfBounds = false;

	   CvRaster srcraster = CvRaster.create(image.height(), image.width(), image.type());
	   srcraster.loadFrom(image);
	   CvRaster dstraster = CvRaster.create(frameHeightPix, frameWidthPix, srcraster.type);

	   int srcwidth = srcraster.cols;
	   int srcheight = srcraster.rows;

	   int dstwidth = dstraster.cols;
	   int dstheight = dstraster.rows;

	   for(int dstRow = 0; dstRow < dstheight; dstRow++) {
		   for(int dstCol = 0; dstCol < dstwidth; dstCol++) {
			   Point srclocation = map(dstRow,dstCol,frameWidthPix,frameHeightPix,reverseImage);

			   if (leftmostCol > srclocation.x) leftmostCol = srclocation.x;
			   if (rightmostCol < srclocation.x) rightmostCol = srclocation.x;
			   if (topmostRow > srclocation.y) topmostRow = srclocation.y;
			   if (bottommostRow < srclocation.y) bottommostRow = srclocation.y;

			   if (srclocation.y < 0 || srclocation.y >= srcheight || 
					   srclocation.x < 0 || srclocation.x >= srcwidth) {
				   dstraster.zero(dstRow, dstCol);
				   outOfBounds = true;
			   }
			   else
				   dstraster.set(dstRow, dstCol, srcraster.get(srclocation.y, srclocation.x));
		   }
	   }

	   frameCut = true;
	   
	   return dstraster.toMat();
   }

   public void addProperties(String section, Properties prop)
   {
      prop.setProperty(section + ".outofbounds", Boolean.toString(outOfBounds));
      prop.setProperty(section + ".cutFrame", Boolean.toString(isCut()));
   }

   public boolean isOutOfBounds()
   {
      return outOfBounds;
   }

   public boolean isCut()
   {
      return !noCut;
   }

   public boolean isFrameCut()
   {
      return frameCut;
   }

   public double calculateFrameWidthPix()
   {
      if (farEdge == null || farEdge.stdDev > worstEdgeStdDevAllowed)
      {
         if (sprocketEdge == null || sprocketEdge.stdDev > worstEdgeStdDevAllowed)
            return -1.0;
         else
         {
            // The frame center in the image will happen along the sprocket hole line
            //  right in between the two images. This will also need to change to the appropriate
            //  place along that line (not right between the two edges. .. actually, to 
            //  make things even simpler for now I will make it exactly between the sprocket
            //  hole and the far edge.
            //
            // first determine the scale factor ...
            //
            double distPix = PolarLineFit.perpendicularDistance(fit,sprocketEdge.c,sprocketEdge.r);
            // we need to increase this amount by the percentage 
            //   that the frame occupies when going from sprocket 
            //   center to the far edge
            double scaleScale = FilmSpec.filmAttribute(filmType,FilmSpec.frameWidthIndex) /
               ((FilmSpec.filmAttribute(filmType,FilmSpec.edgeToEdgeIndex) +
                 (FilmSpec.filmAttribute(filmType,FilmSpec.widthIndex)/2.0)));
            distPix *= scaleScale;

            // distPix is now the calculated frame width in pixels for this frame.

            return distPix;
         }
      }
      else
      {
         // The frame center in the image will happen along the sprocket hole line
         //  right in between the two images. This will also need to change to the appropriate
         //  place along that line (not right between the two edges. .. actually, to 
         //  make things even simpler for now I will make it exactly between the sprocket
         //  hole and the far edge.
         //
         // first determine the scale factor ...
         //
         double distPix = PolarLineFit.perpendicularDistance(fit,farEdge.c, farEdge.r);

         // we need to reduce this amount by the percentage 
         //   that the frame occupies when going from sprocket 
         //   center to the far edge
         double scaleScale = FilmSpec.filmAttribute(filmType,FilmSpec.frameWidthIndex) /
            centerSprocketToFarEdgemm;

         // scaleScale is now the ratio of the frame width to the 
         //  distance from the center of the sprocket to the far edge. This 
         //  will be less than 1.0
         distPix *= scaleScale;

         // distPix is now the calculated frame width in pixels for this frame.

         return distPix;
      }
   }

   private double scale;
   private double imgcr;
   private double imgcc;
   private double unitRow;
   private double unitCol;
   private double vertUnitRow;
   private double vertUnitCol;

   private double fcc;
   private double fcr;

   private void preMapSetupUsingSprocketEdge(int frameWidth, int frameHeight, double scaleMult,
                                             boolean reverseImage, boolean rescale,boolean correctrotation)
   {
      // center of the frame in a coord system centered in the lower
      //  left corner of the frame itself
      fcc = ((double)(frameWidth + 1))/2.0;
      fcr = ((double)(frameHeight + 1))/2.0;

      // for now fit the width of the image between the edges. This will change to 
      //  an estimate of the frame and some overlap (which will need to be specified
      //  also) but for now lets see if i can simply get a good image
      //
//      // The frame center in the image will happen along the sprocket hole line
//      //  right in between the two images. This will also need to change to the appropriate
//      //  place along that line (not right between the two edges. .. actually, to 
//      //  make things even simpler for now I will make it exactly between the sprocket
//      //  hole and the far edge.
//      //
//      // first determine the scale factor ...
//      //
//      double distPix = PolarLineFit.perpendicularDistance(fit,sprocketEdge.c,sprocketEdge.r);
//      // we need to increase this amount by the percentage 
//      //   that the frame occupies when going from sprocket 
//      //   center to the far edge
//      double scaleScale = FilmSpec.filmAttribute(filmType,FilmSpec.frameWidthIndex) /
//         ((FilmSpec.filmAttribute(filmType,FilmSpec.edgeToEdgeIndex) +
//           (FilmSpec.filmAttribute(filmType,FilmSpec.widthIndex)/2.0)));
//      distPix *= scaleScale;

      double distPix = calculateFrameWidthPix();

      scale = distPix / ((double)frameWidth); // original image pixels / frame pixels
      scale *= scaleMult;

      // now scr and scc give the location of the desired point scaled
      //  and relative to the center of the frame. So now we need to
      //  move cfc along the line between the sprocket hole center and 
      //  the far edge starting from the point right in the middle.
      com.jiminger.image.Point nearPoint = 
          PolarLineFit.closest(fit,sprocketEdge.c,sprocketEdge.r);

      double nearPointRow = nearPoint.getRow();
      double nearPointCol = nearPoint.getCol();

      double percentageFromSprocketHoleToNearEdge = 
          -(centerFrameToCenterSprocketXmm / 
            ((FilmSpec.filmAttribute(filmType,FilmSpec.edgeToEdgeIndex) +
              (FilmSpec.filmAttribute(filmType,FilmSpec.widthIndex)/2.0))));

      imgcr = (fit.cr * (1.0 - percentageFromSprocketHoleToNearEdge)) + 
         (nearPointRow * percentageFromSprocketHoleToNearEdge);

      imgcc = (fit.cc * (1.0 - percentageFromSprocketHoleToNearEdge)) + 
         (nearPointCol * percentageFromSprocketHoleToNearEdge);

//      // a unint vector in the direction of the edge of the film within 
//      // the image is given by
//      double nearEdgePolarMag = Math.sqrt( (sprocketEdge.r * sprocketEdge.r) + (sprocketEdge.c * sprocketEdge.c) );
//      unitRow = sprocketEdge.r / nearEdgePolarMag;
//      unitCol = sprocketEdge.c / nearEdgePolarMag;
//
//      // If the row of the fit was negative (and the orientation is horizontal)
//      //  then the edge of the film was too close to the edge of the actual image
//      //  (and we are in  danger of a singluarity ... but we'll fix that another time).
//      //  The unit vector should point from the sprocket hole TO the
//      //  far edge so the row should be reversed. In this case the origin occurrs in 
//      //  between the two.
//      if (!isVertical && unitRow < 0.0) unitRow = -unitRow;
//
//      // If the col of the fit was negative (and the orientation is vertical)
//      //  then the edge of the film was too close to the edge of the actual image
//      //  (and we are in  danger of a singluarity ... but we'll fix that another time).
//      //  The unit vector should point from the sprocket hole TO the
//      //  far edge so the col should reversed. In this case the origin occurrs in 
//      //  between the two.
//      if (isVertical && unitCol < 0.0) unitCol = -unitCol;


      //  Since we are using the 'nearPoint' above anyway, we will actually calculate
      //  the unit vector using these two points (the sprocket hole center and
      //  the closest point on the near edge).
      //
      // diffR,diffC is the r,c representation of the vector from the nearpoint
      //  to the sprocket hole center.
      double diffRow = fit.getRow() - nearPointRow;
      double diffCol = fit.getCol() - nearPointCol;
      double diffMag = Math.sqrt( (diffRow * diffRow) + (diffCol * diffCol) );

//      // this is flipped because the unit vector should go FROM the sprocket
//      //  hole TO the FAR edge.
      unitRow = diffRow/diffMag;
      unitCol = diffCol/diffMag;

      // vertUnit goes DOWN the frame (positive row direction)
      //  parallel to the edge.
      vertUnitRow = reverseImage ? - unitCol : unitCol;
      vertUnitCol = reverseImage ? unitRow : - unitRow;
      
      if (! correctrotation)
          unrotate();

      if (!rescale)
          scale = 1.0;
   }
   
   private void preMapSetupUsingFarEdge(int frameWidth, int frameHeight, double scaleMult,
                                        boolean reverseImage, boolean rescale, boolean correctrotation)
   {
      // center of the frame in a coord system centered in the lower
      //  left corner of the frame itself
      fcc = ((double)(frameWidth + 1))/2.0;
      fcr = ((double)(frameHeight + 1))/2.0;

      // for now fit the width of the image between the edges. This will change to 
      //  an estimate of the frame and some overlap (which will need to be specified
      //  also) but for now lets see if i can simply get a good image
      //
//      // The frame center in the image will happen along the sprocket hole line
//      //  right in between the two images. This will also need to change to the appropriate
//      //  place along that line (not right between the two edges. .. actually, to 
//      //  make things even simpler for now I will make it exactly between the sprocket
//      //  hole and the far edge.
//      //
//      // first determine the scale factor ...
//      //
//      double distPix = PolarLineFit.perpendicularDistance(fit,farEdge.c, farEdge.r);
//
//      // we need to reduce this amount by the percentage 
//      //   that the frame occupies when going from sprocket 
//      //   center to the far edge
//      double scaleScale = FilmSpec.filmAttribute(filmType,FilmSpec.frameWidthIndex) /
//         centerSprocketToFarEdgemm;
//
//      // scaleScale is now the ratio of the frame width to the 
//      //  distance from the center of the sprocket to the far edge. This 
//      //  will be less than 1.0
//      distPix *= scaleScale;
//
//      // distPix is now the calculated frame width in pixels for this frame.

      double distPix = calculateFrameWidthPix();

      scale = distPix / ((double)frameWidth); // original image pixels / frame pixels
      scale *= scaleMult;

      // now scr and scc give the location of the desired point scaled
      //  and relative to the center of the frame. So now we need to
      //  move cfc along the line between the sprocket hole center and 
      //  the far edge starting from the point right in the middle.
      com.jiminger.image.Point farPoint = PolarLineFit.closest(fit,farEdge.c,farEdge.r);

      double farPointRow = farPoint.getRow();
      double farPointCol = farPoint.getCol();

      double percentageFromSprocketHoleToFarEdge = (centerFrameToCenterSprocketXmm / centerSprocketToFarEdgemm);

      imgcr = (fit.cr * (1.0 - percentageFromSprocketHoleToFarEdge)) + 
         (farPointRow * percentageFromSprocketHoleToFarEdge);

      imgcc = (fit.cc * (1.0 - percentageFromSprocketHoleToFarEdge)) + 
         (farPointCol * percentageFromSprocketHoleToFarEdge);

// The following code is used to find a unit vector in the direction from
//  the sprocket hole to the closest point on the far edge. It worked
//  by understanding that the polar line representation of the far 
//  edge was exactly parallel to the line from the sprocket hole,
//  through the axis that bisects the frame, to the closest point on
//  the far edge. This code is commented out because of problems it has
//  when the far edge line passes to the wrong side of the origin. Instead,
//  since we are using the 'farPoint' above anyway, we will actually calculate
//  the unit vector using these two points (the sprocket hole center and
//  the closest point on the far edge).
//
//      // a unint vector in the direction of the edge of the film within 
//      // the image is given by
//      double farEdgePolarMag = Math.sqrt( (farEdge.r * farEdge.r) + (farEdge.c * farEdge.c) );
//      unitRow = farEdge.r / farEdgePolarMag;
//      unitCol = farEdge.c / farEdgePolarMag;
//
//      // If the row of the fit was negative (and the orientation is horizontal)
//      //  then the edge of the film was too close to the edge of the actual image
//      //  (and we are in  danger of a singluarity ... but we'll fix that another time).
//      //  The unit vector should point from the sprocket hole TO the
//      //  far edge so the row should be reversed. In this case the origin occurrs in 
//      //  between the two.
//      if (!isVertical && unitRow < 0.0) unitRow = -unitRow;
//
//      // If the col of the fit was negative (and the orientation is vertical)
//      //  then the edge of the film was too close to the edge of the actual image
//      //  (and we are in  danger of a singluarity ... but we'll fix that another time).
//      //  The unit vector should point from the sprocket hole TO the
//      //  far edge so the col should reversed. In this case the origin occurrs in 
//      //  between the two.
//      if (isVertical && unitCol < 0.0) unitCol = -unitCol;

      //  Since we are using the 'farPoint' above anyway, we will actually calculate
      //  the unit vector using these two points (the sprocket hole center and
      //  the closest point on the far edge).
      //
      // diffR,diffC is the r,c representation of the vector from the sprocket
      //  hole center to the far edge.
      double diffRow = farPointRow - fit.getRow();
      double diffCol = farPointCol - fit.getCol();
      double diffMag = Math.sqrt( (diffRow * diffRow) + (diffCol * diffCol) );
      unitRow = diffRow/diffMag;
      unitCol = diffCol/diffMag;

      // vertUnit goes DOWN the frame (positive row direction)
      //  parallel to the edge.
      vertUnitRow = reverseImage ? - unitCol : unitCol;
      vertUnitCol = reverseImage ? unitRow : - unitRow;

      if (! correctrotation)
          unrotate();

      if (!rescale)
          scale = 1.0;
   }

   private void unrotate()
   {
       if (Math.abs(unitRow) > Math.abs(unitCol))
       {
           unitCol = 0.0;
           unitRow = unitRow > 0.0 ? 1.0 : -1.0;
       }
       else
       {
           unitRow = 0.0;
           unitCol = unitCol > 0.0 ? 1.0 : -1.0;
       }

       if (Math.abs(vertUnitRow) > Math.abs(vertUnitCol))
       {
           vertUnitCol = 0.0;
           vertUnitRow = vertUnitRow > 0.0 ? 1.0 : -1.0;
       }
       else
       {
           vertUnitRow = 0.0;
           vertUnitCol = vertUnitCol > 0.0 ? 1.0 : -1.0;
       }
       
       scale = 1.0;
   }

   private Point map(int r, int c, int frameWidth, int frameHeight, 
                     boolean reverseImage)
   {
      // r and c in frame image coords - with the frame image origin
      //  at the center of the image.
      double cfc = ((double)c) - fcc;
      double rfc = ((double)r) - fcr;

      // scale the row and col passed in.
      double scr = rfc * scale;
      double scc = cfc * scale;

//      // mirror  if reverseImaged
//      if (reverseImage) { scc = -scc; }

      // now move scr pixels up and scc pixels right (either can be negative)
      //  starting from the center and along the unit vector or it's 
      //  90 degree rotation.
      //  unitRow, unitCol is in the direction along the axis of the frame.
      //
      // we also need the unit vector in the 'downward' direction of the 
      //  frame.  Rotating the unit vector 'downward' depends on whether or 
      //  not we're reversed.
      // 
      // downward if we are reversed then [row,col] is -unitCol, unitRow.
      // downward if we are not reversed then [row,col] is unitCol, -unitRow
      //
      // Then if we move SCr along the frame vertical unit vector and SCc along the 
      //  frame axis unit vector, then we should be in the right place in
      //  the image.
      //
      // IM = sCr UnitVertical + sCc UnitAxis
      //
      // UnitAlongAxis is U
      // UnitDownward is V
      //
      // imr = sCr * vr + sCc * ur
      // imc = sCr * vc + sCc * uc
      //
      // then, of course, we need to translate the system to 
      //  the frame center.

      // translate to the correct location in the image

      double imgrow = ((scr * vertUnitRow) + (scc * unitRow)) + imgcr;
      double imgcol = ((scr * vertUnitCol) + (scc * unitCol)) + imgcc;

      return new Point((int)(imgcol + 0.5), (int)(imgrow + 0.5));
   }
}

//   public void drawFrameOutline(TiledImage ti, double resdpi, int frameWidthPix, int frameHeightPix, 
//                                boolean reverseImage, Color overlayColor)
//   {
//      preMapSetup(resdpi,frameWidthPix,frameHeightPix,reverseImage);
//      double resmm = resdpi / FilmSpec.mmPerInch;
//      Point ulp = map(0,0,resmm,frameWidthPix,frameHeightPix, reverseImage);
//      Point urp = map(0,frameWidthPix - 1,resmm, frameWidthPix,frameHeightPix,reverseImage);
//      Point lrp = map(frameHeightPix - 1,frameWidthPix - 1,resmm,frameWidthPix,frameHeightPix, reverseImage);
//      Point llp = map(frameHeightPix - 1,0,resmm,frameWidthPix,frameHeightPix,reverseImage);
//
//      Graphics2D g = ti.createGraphics();
//
//      g.setColor(overlayColor);
//      g.drawLine(ulp.x,ulp.y,urp.x,urp.y);
//      g.drawLine(urp.x,urp.y,lrp.x,lrp.y);
//      g.drawLine(lrp.x,lrp.y,llp.x,llp.y);
//      g.drawLine(llp.x,llp.y,ulp.x,ulp.y);
//   }

//   private Point map(int r, int c, double dpmm, int frameWidth, 
//                     int frameHeight, boolean reverseImage, 
//                     int filmLayout)
//   {
//      // Here we want to do the mapping knowing the two edges of the film.
//      // We will handle any distortion due to crappy scanners here. The 
//      // two edges are assumed to be parallel in reality. In the image space
//      // we will transform one to another with a weighted average. A pixel
//      // the is 0.75 of the way between the two edges would be along a 
//      // line that is (0.75 * edge1 + (1 - 0.75) edge2). This translates
//      // directly to the r,c polar line coordinates. In this case the 
//      // line would be represented by the polar line point 
//      // [ 0.75 * r1 + (1 - 0.75) r2, 0.75 * c1 + (1 - 0.75) c2 ]. This factor
//      // which represents the percetage between the two line we will call
//      // 'w' (for weighting).
//      //
//      // We will use the sprocket center to center the image in the y direction.
//      //  in the y direction one pixel will be one pixel though we may need
//      //  to fix this later.
//
//      // center of the frame in a coord system centered in the lower
//      //  left corner of the frame itself
//      double fcx = ((double)(frameWidth + 1))/2.0;
//      double fcy = ((double)(frameHeight + 1))/2.0;
//
//      // y in frame image coords - with the y -axis frame image origin
//      //  at the center of the image.
//      double yfc = (double)((frameHeight - 1) - r) - fcy;
//
//      double framePercentCol = ((double)c)/((double)(frameWidth - 1));
//
//      // now translate this percentage to a w
//      double w = framePercentCol; // skip this for now ... go edge to edge
//
//      double polr = (w * farEdge.r) + ((1.0 - w) * sprocketEdge.r);
//      double polc = (w * farEdge.c) + ((1.0 - w) * sprocketEdge.c);
//
//      // now, using the line throught the sprocket center and parallel
//      //  to the edges we need to move yfc pixels along it
//      double spw = getSprocketW();
//      double sppolr = (spw * farEdge.r) + ((1.0 - spw) * sprocketEdge.r);
//      double sppolc = (spw * farEdge.c) + ((1.0 - spw) * sprocketEdge.c);
//
//      return null;
//   }

//   public static class FindWeight implements com.jiminger.util.Minimizer.Func, com.jiminger.util.Point
//   {
//      private double row;
//      private double col;
//      private FilmEdge farEdge;
//      private FilmEdge sprocketEdge;
//
//      public static final double [][] start = { { 0.25 } };
//
//      public FindWeight(double row, double col, FilmEdge sprocketEdge, FilmEdge farEdge)
//      {
//         this.row = row;
//         this.col = col;
//         this.farEdge = farEdge;
//         this.sprocketEdge = sprocketEdge;
//      }
//
//      public double func(double [] x)
//      {
//         double w = x[0];
//
//         double newr = w * farEdge.r + (1.0 - w) * sprocketEdge.r;
//         double newc = w * farEdge.c + (1.0 - w) * sprocketEdge.c;
//
//         return com.jiminger.util.PolarLineFit.perpendicularDistance(this,col,row);
//      }
//
//      public double getRow()
//      {
//         return row;
//      }
//
//      public double getCol()
//      {
//         return col;
//      }
//   }

//   // need to map r,c in a frame image back
//   //   into the source image using the known
//   //   data.
//   double [] spec;
//   double fcx;
//   double fcy;
//   double dx;
//   double dy;
//   double costheta;
//   double sintheta;
//
//   private void preMapSetup(double dpmm, int frameWidth, 
//                            int frameHeight, boolean reverseImage)
//   {
//
//      spec = FilmSpec.filmModel(filmType);
//
//      // we need to go from the frame image back into the
//      //  source image. ... we need the vector from the
//      //  center of the theoretical sprocket hole (the one
//      //  that would be there in the frame image) and the
//      //  point being requested.
//
//      // center of the frame in a coord system centered in the lower
//      //  left corner of the frame itself
//      fcx = ((double)(frameWidth + 1))/2.0;
//      fcy = ((double)(frameHeight + 1))/2.0;
//
//      // we know the mm from the center of the frame to the
//      //  center of the sprocket. We need to convert these to
//      //  pixels using the provided resolutiondpmm
//      dx = centerFrameToCenterSprocketXmm * dpmm;
//      dy = spec[FilmSpec.vertDistCenterSprocketToCenterFrameIndex] * dpmm;
//      // the vector [dx,dy] is now the vector representing the sprocket
//      //  position relative to the center of the frame.
//
//
//      // if the sprocket hole in the real image is rotated by theta (counterclockwise
//      //   according to normal carteian space) then as we move from the frame space
//      //   back into the image space we must rotate around the center of the sprocket
//      //   in a CLOCKWISE direction by theta to get into the correct orientation with
//      //   the sprocket hole. It is easiest to visualize this by thinging about an r,c
//      //   passed in that represents the exact center of the frame. Here, spcx,spcy
//      //   will be (for super8 movies where the center of the sprocket is at the same
//      //   'y' value as the center of the frame) -dx, 0. Now, we need to move this
//      //   point around the center of the sprocket hole in the reverse direction that
//      //   the sprocket hole itself is actually rotated in in order to get it into 
//      //   the correct position for mapping back into the original image.
//      //
//      // rotate by the current rotation
//      //  | cos(theta)  -sin(theta) |
//      //  |                         |
//      //  | sin(theta)   cos(theta) |
//      costheta = Math.cos(-fit.rotation);
//      sintheta = Math.sin(-fit.rotation);
//   }
//
//   private Point map(int r, int c, double dpmm, int frameWidth, 
//                     int frameHeight, boolean reverseImage)
//   {
//      // if the image is to be mirrored, then we need to flip
//      //  the x-axis on the requested image.
////         if (reverseImage)
////            c = (frameWidth - 1) - c;
//
//      double tmpd;
//
//      // x and y in frame image coords - with the frame image origin
//      //  at the center of the image.
//      double xfc = (double)c - fcx;
//      double yfc = (double)((frameHeight - 1) - r) - fcy;
//
//      // the sprocket center is now dx,dy. So the vector from
//      //  dx,dy to xfc,yfc is [xfc - dx, yfc - dy]
//      // Note: spc (sprocket centered) x and spcy are now dependent
//      //  upon the resolution passed in.
//      double spcx = xfc - dx;
//      double spcy = yfc - dy;
//
//      // since, in a non reverseImage situation, the sprocket hole is to the 
//      //   right of the image, spcx should be negative.
////         System.out.println("Sprocket centered r,c is " + spcx + "," + spcy + " (from " + r + "," + c + ")");
//
//      // if the sprocket hole in the real image is rotated by theta (counterclockwise
//      //   according to normal carteian space) then as we move from the frame space
//      //   back into the image space we must rotate around the center of the sprocket
//      //   in a CLOCKWISE direction by theta to get into the correct orientation with
//      //   the sprocket hole. It is easiest to visualize this by thinging about an r,c
//      //   passed in that represents the exact center of the frame. Here, spcx,spcy
//      //   will be (for super8 movies where the center of the sprocket is at the same
//      //   'y' value as the center of the frame) -dx, 0. Now, we need to move this
//      //   point around the center of the sprocket hole in the reverse direction that
//      //   the sprocket hole itself is actually rotated in in order to get it into 
//      //   the correct position for mapping back into the original image.
//      //
//      // rotate by the current rotation
//      //  | cos(theta)  -sin(theta) |
//      //  |                         |
//      //  | sin(theta)   cos(theta) |
//      double spcrx = (spcx * costheta) - (spcy * sintheta);
//      double spcry = (spcx * sintheta) + (spcy * costheta);
//
//      // if the image is reversed then the sprockets are on 
//      //  the left side of the frame (in a top to bottom situation)
//      //  and we need to flip the entire coord system symetrically
//      //  around the center of the sprocket. (but for some unknown
//      //  reason, not if the image is lr or rl).
//      if (reverseImage && (filmLayout == FilmSpec.tb || filmLayout == FilmSpec.bt))
//      { spcry = -spcry; spcrx = -spcrx; }
//
//      // now, figure out how to rotate the coords
//      //  according to the layout
//      if (filmLayout == FilmSpec.lr) { tmpd = spcry; spcry = spcrx; spcrx = -tmpd; }
//      else if (filmLayout == FilmSpec.rl) { tmpd = spcry; spcry = -spcrx; spcrx = tmpd; }
//      else if (filmLayout == FilmSpec.bt) { spcry = -spcry; spcrx = -spcrx; }
//
//      // now translate back to the image coords
//      double imx = spcrx + fit.cc;
//
//      // this one gave me a headache
//      double imy = reverseImage ? (fit.cr + spcry) : (fit.cr - spcry);
//
//      return new Point((int)(imx + 0.5), (int)(imy + 0.5));
//   }

