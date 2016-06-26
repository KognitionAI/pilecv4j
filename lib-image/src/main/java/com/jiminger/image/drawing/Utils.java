package com.jiminger.image.drawing;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import com.jiminger.image.CvRaster;
import com.jiminger.image.Point;

public class Utils {
	public static final ColorModel grayColorModel;
	
	static {
		ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
		int[] nBits = { 8 };
		grayColorModel = new ComponentColorModel(cs, nBits, false, true, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
	}
	
	public static BufferedImage mat2Img(Mat in) {
		BufferedImage out;
		byte[] data = new byte[in.width() * in.height() * (int)in.elemSize()];
		int type;
		in.get(0, 0, data);

		if(in.channels() == 1)
			type = BufferedImage.TYPE_BYTE_GRAY;
		else
			type = BufferedImage.TYPE_3BYTE_BGR;

		out = new BufferedImage(in.width(), in.height(), type);

		out.getRaster().setDataElements(0, 0, in.width(), in.height(), data);
		return out;
	}

	public static BufferedImage mat2Img(CvRaster in, IndexColorModel colorModel) {
		BufferedImage out;

		if(in.channels != 1 || CvType.depth(in.type) != CvType.CV_8U)
			throw new IllegalArgumentException("Cannot convert a Mat to a BufferedImage with a colorMap if the Mat has more than one channel);");

		out = new BufferedImage(in.cols, in.rows, BufferedImage.TYPE_BYTE_INDEXED, colorModel);

		out.getRaster().setDataElements(0, 0, in.cols, in.rows, in.data);
		return out;
	}

	public static void print(String prefix, Mat im) {
		System.out.println(prefix + " { depth=(" + CvType.ELEM_SIZE(im.type()) + ", " + im.depth() + "), channels=" + im.channels() + " HxW=" + im.height() + "x" + im.width()  + " }");
	}
	   
	
	public static class DumbPoint implements Point {
		private double r;
		private double c;

		public DumbPoint(double r, double c) {
			this.r = r;
			this.c = c;
		}

		public double getRow() { return r; }
		public double getCol() { return c; }
	}


	static public Point closest(Point x, double polarx, double polary) {
		// Here we use the description for the perpendicularDistance.
		//  if we translate X0 to the origin then Xi' (defined as
		//  Xi translated by X0) will be at |P| - (P.X0)/|P| (which 
		//  is the signed magnitude of the X0 - Xi where the sign will
		//  be positive if X0 X polar(P) is positive and negative 
		//  otherwise (that is, if X0 is on the "lower" side of the polar
		//  line described by P)) along P itself. So:
		//
		// Xi' = (|P| - (P.X0)/|P|) Pu = (|P| - (P.X0)/|P|) P/|P|
		//     = (1 - (P.X0)/|P|^2) P (where Pu is the unit vector in the P direction)
		//
		// then we can translate it back by X0 so that gives:
		//
		// Xi = (1 - (P.X0)/|P|^2) P + X0 = c P + X0
		//  where c = (1 - (P.X0)/|P|^2)
		double Pmagsq = (polarx * polarx) + (polary * polary);
		double PdotX0 = (x.getRow() * polary) + (x.getCol() * polarx);

		double c = (1.0 - (PdotX0 / Pmagsq));
		return new DumbPoint( (c * polary) + x.getRow(), (c * polarx) + x.getCol() );
	}

	public static void drawCircle(Point p, Mat ti, Color color) {
		drawCircle(p,ti,color,10);
	}

	public static void drawCircle(int row, int col, Mat ti, Color color) {
		drawCircle(row,col,ti,color,10);
	}

	public static void drawCircle(Point p, Mat ti, Color color, int radius) {
		Imgproc.circle(ti, new org.opencv.core.Point(((int)(p.getCol() + 0.5))-radius, ((int)(p.getRow() + 0.5))-radius), 
				radius, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
	}

	public static void drawCircle(int row, int col, Mat ti, Color color, int radius) {
		Imgproc.circle(ti, new org.opencv.core.Point(((int)((double)col + 0.5))-radius, ((int)((double)row + 0.5))-radius), 
				radius, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
	}

	public static void drawCircle(int row, int col, Graphics2D g, Color color, int radius) {
		g.setColor(color);

		g.drawOval(((int)((double)col + 0.5))-radius,
				((int)((double)row + 0.5))-radius,
				2 * radius,2 * radius);

	}
	
	public static void drawCircle(int row, int col, Graphics2D g, Color color) {
		drawCircle(row,col,g,color,10);
	}
	
	public static void drawCircle(Point p, Graphics2D g, Color color) {
		drawCircle((int)p.getRow(),(int)p.getCol(),g,color,10);
	}

	public static void drawBoundedPolarLine(Point bound1, Point bound2, double r, double c, Mat ti, Color color) {
		drawLine(closest(bound1, c, r), closest(bound2, c, r), ti, color);
	}
	
	public static void drawLine(Point p1, Point p2, Mat ti, Color color) {
		Imgproc.line(ti, new org.opencv.core.Point(p1.getCol(), p1.getRow()), 
				new org.opencv.core.Point(p2.getCol(),p2.getRow()), 
				new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
	}
	
	public static void drawLine(Point p1, Point p2, Graphics2D g, Color color) {
		g.setColor(color);
		g.drawLine((int)(p1.getCol() + 0.5),(int)(p1.getRow() + 0.5),(int)(p2.getCol() + 0.5),(int)(p2.getRow() + 0.5));
	}

	
	static public void drawPolarLine(double r, double c, Mat ti, Color color) {
		drawPolarLine(r,c,ti,color,0,0,ti.rows()-1,ti.cols()-1);
	}

	static public void drawPolarLine(double r, double c, Mat ti, Color color, 
			int boundingr1, int boundingc1, int boundingr2, int boundingc2) {
		drawPolarLine(r, c, ti, color, boundingr1, boundingc1, boundingr2, boundingc2, 0, 0);
	}

	static public void drawPolarLine(double r, double c, Mat ti, Color color, 
			int boundingr1, int boundingc1, int boundingr2, int boundingc2,
			int translater, int translatec) {
		int tmpd;
		if (boundingr1 > boundingr2)
		{ tmpd = boundingr1; boundingr1 = boundingr2; boundingr2 = tmpd; }

		if (boundingc1 > boundingc2)
		{ tmpd = boundingc1; boundingc1 = boundingc2; boundingc2 = tmpd; }

		// a polar line represented by r,c is a perpendicular to
		//  the line from the origin to the point r,c. The line
		//  from the origin to this point in rad,theta is given
		//  by:
		//
		//     rad = sqrt(r^2 + c^2)
		//     theta = tan^-1(r/c)
		//        (where theta is measured from the top of the 
		//         image DOWN to the point r,c)
		//
		//  anyway - the line is represented by:
		//   x cos(theta) + y sin (theta) = r

		double rad = Math.sqrt((r * r) + (c * c));

		// we need to find the endpoints of the line:
		int r1, c1, r2, c2;

		// lets remove the simple possiblities
		if (c == 0.0)
		{
			r1 = r2 = (int)(rad + 0.5);
			c1 = boundingc1;
			c2 = boundingc2;
		}
		else if (r == 0.0)
		{
			c1 = c2 = (int)(rad + 0.5);
			r1 = boundingr1;
			r2 = boundingr2;
		}
		else
		{
			double sintheta = r / rad;
			double costheta = c / rad;

			// x cos th + y sin th = r =>
			// x (xc/r) + y (yc/r) = r (by definition of sin and cos) =>
			// x xc + y yc = r^2 =>
			// X.Xc = r^2 - (no duh!)

			// find the points at the boundaries

			// where does the line intersect the left/right boundary
			//  bc costh + ir sinth = r =>
			//
			//        r - bc costh
			//  ir = -------------
			//           sinth
			//
			double leftIntersetingRow = (rad - (((double)boundingc1) * costheta)) / sintheta;
			double rightIntersetingRow = (rad - (((double)boundingc2) * costheta)) / sintheta;

			// where does the line intersect the top/bottom boundary
			//  ic costh + br sinth = r =>
			//
			//        r - br sinth
			//  ic = -------------
			//           costh
			//
			double topIntersectingCol = (rad - (((double)boundingr1) * sintheta)) / costheta;
			double botIntersectingCol = (rad - (((double)boundingr2) * sintheta)) / costheta;

			// now, which pair works the best
			c1 = r1 = -1;
			if (leftIntersetingRow >= (double)boundingr1 && leftIntersetingRow <= (double)boundingr2)
			{ c1 = boundingc1; r1 = (int)(leftIntersetingRow + 0.5); }
			else if (topIntersectingCol >= (double)boundingc1 && topIntersectingCol <= (double)boundingc2)
			{ c1 = boundingr1; r1 = (int)(topIntersectingCol + 0.5); }
			else if (rightIntersetingRow >= (double)boundingr1 && rightIntersetingRow <= (double)boundingr2)
			{ c1 = boundingc2; r1 = (int)(rightIntersetingRow + 0.5); }
			else if (botIntersectingCol >= (double)boundingc1 && botIntersectingCol <= (double)boundingc2)
			{ c1 = boundingr2; r1 = (int)(botIntersectingCol + 0.5); }

			if (c1 == -1 && r1 == -1) // no part of the line intersects the box
			//		         {
				//		            System.out.println( " line " + r + "," + c + " does not intesect " +
				//		                                boundingr1 + "," + boundingc1 + "," + boundingr2 + "," + boundingc2);
				return;
			//		         }

			// now search in the reverse direction for the other point
			c2 = r2 = -1;
			if (botIntersectingCol >= (double)boundingc1 && botIntersectingCol <= (double)boundingc2)
			{ c2 = boundingr2; r2 = (int)(botIntersectingCol + 0.5); }
			else if (rightIntersetingRow >= (double)boundingr1 && rightIntersetingRow <= (double)boundingr2)
			{ c2 = boundingc2; r2 = (int)(rightIntersetingRow + 0.5); }
			else if (topIntersectingCol >= (double)boundingc1 && topIntersectingCol <= (double)boundingc2)
			{ c2 = boundingr1; r2 = (int)(topIntersectingCol + 0.5); }
			else if (leftIntersetingRow >= (double)boundingr1 && leftIntersetingRow <= (double)boundingr2)
			{ c2 = boundingc1; r2 = (int)(leftIntersetingRow + 0.5); }

			// now, the two points should not be the same ... but anyway
		}


		Imgproc.line(ti, new org.opencv.core.Point(c1 + translatec, r1 + translater), 
				new org.opencv.core.Point(c2 + translatec,r2 + translater), 
				new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
	}
	public static BufferedImage dbgImage = null;
	public static Graphics2D wrap(CvRaster cvraster) {
		if (cvraster.channels != 1 && CvType.depth(cvraster.type) != CvType.CV_8U)
			throw new IllegalArgumentException("can only get Graphics2D for an 8-bit CvRaster with 1 channel. Was passed " + cvraster);
		DataBufferByte db = new DataBufferByte((byte[])cvraster.data, cvraster.rows * cvraster.cols);
		WritableRaster raster = Raster.createInterleavedRaster(db, cvraster.cols, cvraster.rows, cvraster.cols, 1, new int[] { 0 },new java.awt.Point(0, 0));
		BufferedImage bi = new BufferedImage(Utils.grayColorModel, raster, false, null);
		dbgImage = bi;
		return bi.createGraphics();
	}
	   

}
