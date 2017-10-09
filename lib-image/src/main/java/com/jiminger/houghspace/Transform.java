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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import com.jiminger.houghspace.internal.Mask;
import com.jiminger.houghspace.internal.GradientDirectionMask;
import com.jiminger.image.CvRaster;
import com.jiminger.image.ImageFile;
import com.jiminger.image.WeightedPoint;
import com.jiminger.image.drawing.Utils;
import com.jiminger.nr.Minimizer;
import com.jiminger.nr.MinimizerException;

public class Transform {
   public final double quantFactor;
   public final Mask mask;
   public final GradientDirectionMask gradDirMask;
   public final double gradientDirSlopDeg;
   public final Model model;

   public Transform(Model model, double quantFactor, double scale, double gradientDirSlopDeg) {
	   this.quantFactor = quantFactor;
	   this.mask = Mask.generateMask(model,quantFactor, scale);
	   this.gradDirMask = GradientDirectionMask.generateGradientMask(model,model.featureWidth(),model.featureHeight(),quantFactor);
	   this.gradientDirSlopDeg = gradientDirSlopDeg;
	   this.model = model;
   }

   /**
    * This method assumes raster is an edge detected image. If gradient raster is supplied
    *   then it will be used to greatly improve the results.
    */
   public HoughSpace transform(CvRaster raster, CvRaster gradientRaster,int houghThreshold)
   {
      int height = raster.rows;
      int width = raster.cols;
      return transform(raster,gradientRaster,houghThreshold,0,height-1,0,width-1);
   }

   public HoughSpace transform(CvRaster raster, CvRaster gradientRaster,int houghThreshold,
                               int rowstart, int rowend, int colstart, int colend)
   {
      byte [] image = (byte[])raster.data;
      int height = raster.rows;
      int width = raster.cols;

      byte [] gradientDirImage = gradientRaster == null ? null : (byte[])gradientRaster.data;

      // the size of the hough space should be quantFactor smaller
      int htheight = (int)(((double)height)/quantFactor) + 1;
      int htwidth = (int)(((double)width)/quantFactor) + 1;

      short [] ret = new short [htheight * htwidth];

      HoughSpaceEntryManager hsem = new HoughSpaceEntryManager(quantFactor);

      if (rowstart < 0) rowstart = 0;
      if (rowend >= height) rowend = height-1;
      if (colstart < 0) colstart = 0;
      if (colend >= width) colend = width-1;

      houghTransformNative(image,width,height,gradientDirImage,
                           mask.mask,mask.mwidth,mask.mheight,mask.maskcr,mask.maskcc,
                           gradDirMask.mask,gradDirMask.mwidth,gradDirMask.mheight,gradDirMask.maskcr,gradDirMask.maskcc,
                           gradientDirSlopDeg, quantFactor,ret, htwidth, htheight, hsem, houghThreshold,
                           rowstart,rowend,colstart,colend);

      hsem.entryMap.clear(); // help the gc

      return new HoughSpace(ret,htwidth,htheight,quantFactor,hsem.entries);
   }

                                        
   native public void houghTransformNative(byte [] image, int width, int height, byte [] gradientDirImage,
                                           byte [] mask, int maskw, int maskh, int maskcr, int maskcc,
                                           short [] gradDirMask, int gdmaskw, int gdmaskh, int gdmaskcr, int gdmaskcc,
                                           double gradientDirSlopDeg, double quantFactor,
                                           short [] ret, int htwidth, int htheight, HoughSpaceEntryManager hsem,
                                           int houghThreshold, int rowstart, int rowend, int colstart, int colend);
   
   /**
    * This method does not do much any more. Not it simply writes the inverse transform
    *  (that is, the edge pixels identified by the transform) back into the image
    *  for debugging purposes.
    */
   public List<HoughSpaceEntry> inverseTransform(HoughSpace houghSpace, CvRaster ti, byte overlayPixelValue, byte peakCircleColorValue) {
      List<HoughSpaceEntry> sortedSet = new LinkedList<HoughSpaceEntry>();

      sortedSet.addAll(houghSpace.backMapEntries);
      Graphics2D g = Utils.wrap(ti);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      Collections.sort(sortedSet, new HoughSpaceEntry.HSEComparator());
      Color peakCircleColor = new Color(peakCircleColorValue,peakCircleColorValue,peakCircleColorValue);

      if (ti != null) {
         System.out.println("Constructing reverse hough transform image.");

         byte[] overlayPixel = new byte[] { overlayPixelValue };
         for (HoughSpaceEntry e : sortedSet) {
            int eir = e.ir;
            int eic = e.ic;

            Utils.drawCircle(eir, eic, g, peakCircleColor);

            for (java.awt.Point p : e.contributingImagePoints)
               ti.set(p.y, p.x, overlayPixel);
         }
      }
      
      try {
    	  ImageFile.writeImageFile(Utils.dbgImage, "bi.bmp");
      } catch (IOException ioe) {
    	  ioe.printStackTrace();
      }

      return sortedSet;
   }

   public Mat getTransformImage(HoughSpace houghSpace0)
   {
      short [] houghSpace = houghSpace0.houghSpace;
      int width = houghSpace0.hswidth;
      int height = houghSpace0.hsheight;

      CvRaster gradRaster = CvRaster.create(height, width, CvType.CV_8UC1); 

      int max = 0;
      for (int i = 0; i < houghSpace.length; i++)
      {
         int count = houghSpace[i];
         if (max < count)
            max = count;
      }

      byte[] dest = (byte[])gradRaster.data;
      for(int i = 0; i < houghSpace.length; i++)
      {
         int intVal = (int)(((double)(houghSpace[i]) / (double)max) * 255.0);
         if(intVal < 0)
            intVal = 0;
         else
            if(intVal > 255)
               intVal = 255;

         dest[i] = (byte)intVal;
      }

      return gradRaster.toMat();
   }

   public List<Cluster> cluster(List<HoughSpaceEntry> houghEntries, double percentModelCoverage)
   {
      List<Cluster> ret = new ArrayList<Cluster>();

      double minDist = ((mask.mwidth > mask.mheight ? mask.mheight : mask.mwidth) + 1) * percentModelCoverage;

      // this is going to do rather simplistic clustering.
      for (HoughSpaceEntry cur : houghEntries)
      {
         if (ret.size() == 0)
            ret.add(new Cluster(cur));
         else // see if the cur belongs within a current cluster
         {
            boolean done = false;
            for (int i = 0; i < ret.size() && !done; i++)
            {
               Cluster c = (Cluster)ret.get(i);
               if (c.distance(cur) <= minDist)
               {
                  c.add(cur);
                  done = true;
               }
            }
            if (!done)
               ret.add(new Cluster(cur));
         }
      }

      return ret;
   }

   /**
    * This method will take a Cluster and use it to minimize the sum of square error
    *  with the error against the model that would fit the actual edge pixels.
    *  This is what finds the actual feature from the cluster. The passed image
    *  and overlay values are for bookkeeping only. A null ti means ignore book
    *  keeping.
    */
   public Fit bestFit(Cluster cluster,CvRaster ti, byte overlayPixelValueRemovedEdge, byte overlayPixelValueEdge)
      throws MinimizerException
   {
      return bestFit(cluster,ti,overlayPixelValueRemovedEdge,overlayPixelValueEdge,null);
   }

   /**
    * This method will take a Cluster and use it to minimize the sum of square error
    *  with the error against the model that would fit the actual edge pixels.
    *  This is what finds the actual feature from the cluster. The passed image
    *  and overlay values are for bookkeeping only. A null ti means ignore book
    *  keeping.
    */
   public Fit bestFit(Cluster cluster, CvRaster ti, byte overlayPixelValueRemovedEdge, byte overlayPixelValueEdge, List<java.awt.Point> savedPruned)
      throws MinimizerException {
      // need to go through the raster around the cluster using the highest 
      //  count cluster value

      // find the original pixels that contributed to this
      //  value.
      // there is a sprocket centered at e.r, e.c so we 
      //  need to see which pixels contribute to it
      List<java.awt.Point> edgeVals = new ArrayList<java.awt.Point>();
      edgeVals.addAll(cluster.getContributingEdges());

      // now edgevals contains the list of all of the edge values that contributed to 
      //  this cluster.

      double [] result = null;

      boolean pruning = true;
      List<java.awt.Point> pruned = new ArrayList<java.awt.Point>();
      double stdDev = -1.0;
      for (boolean done = false;! done;) {
         pruned.clear();
         FitSumSquaresDist func = new FitSumSquaresDist(edgeVals,model);
         Minimizer m = new Minimizer(func);
         double [] params = new double[4];
         params[0] = (double)cluster.imageCol();
         params[1] = (double)cluster.imageRow();
         params[2] = 0.0;
         params[3] = 1.0;
         /*double sumSqErr =*/ m.minimize(params);
         result = m.getFinalPostion();
         stdDev = func.stdDev;

         if (pruning)
         {
            pruning = func.prune(func.stdDev * 3.0,result,pruned);

//  This will remove one pixel at a time until the std dev 
//   is below some value. It's too slow.
//            if (!pruning && func.stdDev > 1.0)
//            {
//               pruning = true;
//               func.pruneFurthest(pruned);
//            }
         }
         
         // if we want to write a debug image, then do it.
         byte[] overlayRemovedEdgePixel = new byte[] { overlayPixelValueRemovedEdge }; 
         if (ti != null) {
            if (pruned.size() > 0) {
               for (java.awt.Point p : pruned)
                  ti.set(p.y,p.x,overlayRemovedEdgePixel);
            }
         }

         if (savedPruned != null)
            savedPruned.addAll(pruned);

         if (!pruning) // if we are not pruning the exit
            done = true;
      }

      if (ti != null) {
    	 byte[] overlayPixelEdge = new byte[] { overlayPixelValueEdge };
         for (java.awt.Point p : edgeVals)
            ti.set(p.y,p.x,overlayPixelEdge);
      }

      return new Fit(result[1],result[0],result[3],result[2],cluster, stdDev, edgeVals);
   }

   public static void drawClusters(List<Cluster> clusters, CvRaster ti, byte color) {
      Graphics2D g = Utils.wrap(ti);
      g.setColor(new Color(color,color,color));
      for (Cluster c : clusters)
         drawCircle(g,c.imageRow(),c.imageCol());
   }

   public static void drawFits(List<Transform.Fit> fits, CvRaster ti, byte color) {
      Graphics2D g = Utils.wrap(ti);
      g.setColor(new Color(color,color,color));
      for (Fit c : fits)
         drawCircle(g,(int)Math.round(c.cr),(int)Math.round(c.cc));
   }

   public static class HoughSpaceEntryManager
   {
      private double quantFactor;
      public Map<java.awt.Point,HoughSpaceEntry> entryMap = new HashMap<java.awt.Point,HoughSpaceEntry>();
      public List<HoughSpaceEntry> entries = new ArrayList<HoughSpaceEntry>();

      HoughSpaceEntryManager(double quantFactor)
      {
         this.quantFactor = quantFactor;
      }

      public void addHoughSpaceEntryContributor(int imrow, int imcol, int hsr, int hsc, int count)
      {
         // find the entry from the hough space position
         java.awt.Point hsrc = new java.awt.Point(hsc,hsr);
         HoughSpaceEntry e = (HoughSpaceEntry)entryMap.get(hsrc);
         if (e == null)
         {
            e = new HoughSpaceEntry(hsr,hsc,count,quantFactor);
            entryMap.put(hsrc,e);
            entries.add(e);
         }

         e.addContribution(imrow,imcol);
      }
   }

   public static void drawCircle(Graphics2D g, int r, int c)
   {
      int radius = 10;
      g.drawOval(c-radius,r-radius,2 * radius,2 * radius);
   }

   public static class HoughSpaceEntry 
   {
      public int r;
      public int c;
      public int count;
      public int ir;
      public int ic;
      public double quantFactor;
      public List<java.awt.Point> contributingImagePoints = new ArrayList<java.awt.Point>();

      public HoughSpaceEntry(int r, int c, int count, double quantFactor)
      {
         this.r = r;
         this.c = c;
         this.quantFactor = quantFactor;
         this.count = count;
         this.ir = (int)((this.r + 1) * this.quantFactor);
         this.ic = (int)((this.c + 1) * this.quantFactor);
      }

      public void addContribution(int imr, int imc)
      {
         contributingImagePoints.add(new java.awt.Point(imc,imr));
      }

      @Override
      public boolean equals(Object o)
      {
         HoughSpaceEntry e= (HoughSpaceEntry)o;
         return (e.r == r && e.c == c && e.count == count);
      }

      @Override
      public int hashCode()
      {
         return new Integer(r).hashCode() +
            new Integer(c).hashCode() + 
            new Integer(count).hashCode();
      }

      public String toString()
      {
         return "(" + r + "," + c + "," + count + ")->" + contributingImagePoints;
      }

      public static class HSEComparator implements Comparator<HoughSpaceEntry>
      {
         @Override
         public int compare(HoughSpaceEntry o1, HoughSpaceEntry o2)
         {
            // reverse order
            return o2.count - o1.count;
         }
      }
   }

   public static class HoughSpace
   {
      public HoughSpace(short [] houghSpace, int width, int height,
                        double quantFactor, List<HoughSpaceEntry> backMapEntries)
      {
         this.houghSpace = houghSpace;
         this.hswidth = width;
         this.hsheight = height;
         this.quantFactor = quantFactor;
         this.backMapEntries = backMapEntries;
      }

      public short [] houghSpace;
      public int hswidth;
      public int hsheight;
      public double quantFactor;
      public List<HoughSpaceEntry> backMapEntries;
   }

   public static class Cluster implements WeightedPoint
   {
      private double ccr;
      private double ccc;
      private List<HoughSpaceEntry> choughEntries;
      private boolean cisSorted = false;
      private double cquantFactor;
//      private int totalcount = -1;
      private List<java.awt.Point> edgeVals = null;

      public Cluster()
      {
         choughEntries = new ArrayList<HoughSpaceEntry>();
      }

      public Cluster(HoughSpaceEntry e)
      {
         choughEntries = new ArrayList<HoughSpaceEntry>();
         add(e);
      }

      public int totalCount()
      {
         return getContributingEdges().size();
      }

      public int imageRow()
      {
         return (int)((ccr + 1.0) * cquantFactor);         
      }

      public int imageCol()
      {
         return (int)((ccc + 1.0) * cquantFactor);         
      }

      public double row()
      {
         return ccr;
      }

      public double col()
      {
         return ccc;
      }

      public void add(HoughSpaceEntry e)
      {
         cisSorted = false;

         if (choughEntries.size() == 0)
         {
            ccr = ((double)e.r);
            ccc = ((double)e.c);
            choughEntries.add(e);
            cquantFactor = e.quantFactor;
         }
         else
         {
            double n = (double)(choughEntries.size());
            // find the centroid by averaging ... 
            //  if ccr,ccc is already an average
            //  of the current houghEntries
            //  then we can do an incremental 
            //  average.
            ccr = (( ccr * n ) + ((double)e.r))/ ( n + 1.0 );
            ccc = (( ccc * n ) + ((double)e.c))/ ( n + 1.0 );
            choughEntries.add(e);
         }
      }

      public double distance(HoughSpaceEntry e)
      {
         double dr = ccr - ((double)e.r);
         double dc = ccc - ((double)e.c);
         return Math.sqrt((dr * dr) + (dc * dc));
      }

      public String toString()
      {
         return "(" + imageRow() + "," + imageCol() + ")";
      }

      public int getMaxCount()
      {
         sortCheck();
         return ((HoughSpaceEntry)choughEntries.get(0)).count;
      }

      public List<HoughSpaceEntry> getHoughEntries()
      {
         sortCheck();
         return choughEntries;
      }

      public synchronized List<java.awt.Point> getContributingEdges()
      {
         if (edgeVals == null)
         {
            edgeVals = new ArrayList<java.awt.Point>();
            List<HoughSpaceEntry> houghEntries = getHoughEntries();

            // we want to accumulate all of the edge vals that went
            //  into this cluster
            for (int hei = 0; hei < houghEntries.size(); hei++)
            {
               HoughSpaceEntry e = (HoughSpaceEntry)houghEntries.get(hei);

               for (java.awt.Point p : e.contributingImagePoints)
               {
                  if (!edgeVals.contains(p))
                     edgeVals.add(new java.awt.Point(p.x,p.y));
               }
            }
         }

         return Collections.unmodifiableList(edgeVals);
      }

      private void sortCheck()
      {
         if (!cisSorted)
         {
            Collections.sort(choughEntries,new HoughSpaceEntry.HSEComparator());
            cisSorted = true;
         }
      }

      // Point interface
      @Override
      public double getRow() { return (double)imageRow(); }
      @Override
      public double getCol() { return (double)imageCol(); }
      @Override
      public double getWeight() { return (double)totalCount(); }

   }

   public static class FitSumSquaresDist implements Minimizer.Func
   {
      private List<java.awt.Point> edgeVals;
      private Model sm;
      public java.awt.Point furthest;
      public double maxdist;
      public double stdDev;
      private boolean flipYAxis;

      public FitSumSquaresDist(List<java.awt.Point> edgeVals, Model sm)
      {
         this.edgeVals = edgeVals;
         this.sm = sm;
         this.flipYAxis = sm.flipYAxis();
      }

      public boolean prune(double maxDist, double [] x, List<java.awt.Point> pruned)
      {
         boolean ret = false;
         double cx = x[0];
         double cy = x[1];

         for (int i = edgeVals.size() - 1; i >= 0; i--)
         {
            java.awt.Point p = (java.awt.Point)edgeVals.get(i);
            double vx = (double)p.x - cx;
            double vy = (double)p.y - cy;
            double dist = sm.distance(vx,vy,x[2],x[3]);

            if (dist >= maxDist)
            {
               pruned.add(edgeVals.remove(i));
               ret = true;
            }
         }

         return ret;
      }

      public void pruneFurthest(List<java.awt.Point> pruned)
      {
         if (furthest != null)
         {
            boolean done = false;
            for (int i = 0; i < edgeVals.size() && !done; i++)
            {
               if (furthest == edgeVals.get(i))
               {
                  edgeVals.remove(i);
                  pruned.add(furthest);
                  System.out.print(".");
                  done = true;
               }
            }
         }
      }

      public double func(double [] x)
      {
         double cx = x[0];
         double cy = x[1];

         maxdist = -1.0;

         double ret = 0.0;
         for (int i = 0; i < edgeVals.size(); i++)
         {
            java.awt.Point p = (java.awt.Point)edgeVals.get(i);
            // now, if the sprocket is centered at cx,cy - 
            //  we need to translate the point p into the sprocket 
            //  coords
            double vx = (double)p.x - cx;
            double vy = (double)p.y - cy;

            if (flipYAxis)
               vy = - vy;

            double dist = sm.distance(vx,vy,x[2],x[3]);

            if (maxdist < dist)
            {
               maxdist = dist;
               furthest = p;
            }

            ret += (dist * dist);
         }

         stdDev = Math.sqrt(ret / (double)edgeVals.size());

         return ret;
      }
   }

   public static class Fit implements WeightedPoint
   {
      public double cr; // center of sprocket instance row
      public double cc; // center of sprocket instance col
      public double rotation; // orientation of the sprocket instance
      public double scale; // scale of the sprocket
      public Cluster sourceCluster;
      public double stdDev;
      public List<java.awt.Point> edgeVals;
      public int rank;

      public Fit(double cr, double cc, double scale, double rotation, 
                 Cluster sourceCluster, double stdDev, List<java.awt.Point> edgeVals)
      {
         this.cr = cr;
         this.cc = cc;
         this.rotation = rotation;
         this.scale = scale;
         this.sourceCluster = sourceCluster;
         this.stdDev = stdDev;
         this.edgeVals = edgeVals;
      }

      public String toString()
      {
         return "[(rc)=(" + cr + "," + cc +") * " + scale + " ang(deg)=" + (rotation * (180.0/Math.PI)) + "] sd=" + 
            stdDev + " " + edgeVals.size();
      }

      @Override
      public double getRow() { return cr; }
      @Override
      public double getCol() { return cc; }

      public int imageRow() { return (int)(cr + 0.5); }
      public int imageCol() { return (int)(cc + 0.5); }

      @Override
      public double getWeight() { return (double)rank; }

      public static class StdDeviationOrder implements Comparator<Transform.Fit>
      {
         @Override
         public int compare(Transform.Fit o1, Transform.Fit o2)
         {
            return o1.stdDev > o2.stdDev ? 1 :
               ((o1.stdDev == o2.stdDev) ? 0 : -1);
         }
      }

      public static class EdgeCountOrder implements Comparator<Transform.Fit>
      {
         @Override
         public int compare(Transform.Fit o1, Transform.Fit o2)
         {
            return o2.edgeVals.size() - o1.edgeVals.size();
         }
      }
   }

//   private static byte byteify(int i)
//   {
//      return i > 127 ? (byte)(i - 256) : (byte)i;
//   }

   private static int intify(byte b)
   {
      return (b < 0) ? ((int)b) + 256 : (int)b;
   }

}
