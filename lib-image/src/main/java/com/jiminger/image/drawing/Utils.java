package com.jiminger.image.drawing;

import static java.awt.image.BufferedImage.TYPE_3BYTE_BGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;
import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR_PRE;
import static java.awt.image.BufferedImage.TYPE_BYTE_GRAY;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE;
import static java.awt.image.BufferedImage.TYPE_INT_BGR;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_555_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_565_RGB;
import static java.awt.image.BufferedImage.TYPE_USHORT_GRAY;
import static org.opencv.core.CvType.CV_16S;
import static org.opencv.core.CvType.CV_16U;
import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8U;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
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
        final ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        final int[] nBits = { 8 };
        grayColorModel = new ComponentColorModel(cs, nBits, false, true, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
    }

    public static BufferedImage mat2Img(final Mat in) {
        final CvRaster raster = CvRaster.create(in);
        final Object data = raster.data;
        int type;

        final int inChannels = in.channels();
        if (inChannels == 1) { // assume gray
            switch (CvType.depth(in.type())) {
                case CV_8U:
                case CV_8S:
                    type = BufferedImage.TYPE_BYTE_GRAY;
                    break;
                case CV_16U:
                case CV_16S:
                    type = BufferedImage.TYPE_USHORT_GRAY;
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Cannot convert a Mat with a type of " + CvType.typeToString(in.type()) + " to a BufferedImage");
            }
        } else if (inChannels == 3) {
            final int cvDepth = CvType.depth(in.type());
            if (cvDepth != CV_8U && cvDepth != CV_8S)
                throw new IllegalArgumentException("Cannot convert RGB Mats with elements larger than a byte yet.");
            type = BufferedImage.TYPE_3BYTE_BGR;
        } else if (inChannels == 4)
            throw new IllegalArgumentException("Can't handle alpha channel yet.");
        else
            throw new IllegalArgumentException("Can't handle an image with " + inChannels + " channels");

        final BufferedImage out = new BufferedImage(in.width(), in.height(), type);

        out.getRaster().setDataElements(0, 0, in.width(), in.height(), data);
        return out;
    }

    public static BufferedImage mat2Img(final CvRaster in, final IndexColorModel colorModel) {
        BufferedImage out;

        if (in.channels != 1 || CvType.depth(in.type) != CvType.CV_8U)
            throw new IllegalArgumentException("Cannot convert a Mat to a BufferedImage with a colorMap if the Mat has more than one channel);");

        out = new BufferedImage(in.cols, in.rows, BufferedImage.TYPE_BYTE_INDEXED, colorModel);

        out.getRaster().setDataElements(0, 0, in.cols, in.rows, in.data);
        return out;
    }

    private static CvRaster argbDataBufferByteToCvRaster(final DataBufferByte bb, final int h, final int w, final int[] lookup) {
        final boolean hasAlpha = lookup[0] != -1;
        final CvRaster raster = CvRaster.create(h, w, hasAlpha ? CvType.CV_8UC4 : CvType.CV_8UC3);
        final byte[] inpixels = bb.getData();
        final byte[] outpixels = (byte[]) raster.data;
        final int numElements = h * w;
        final int skip = hasAlpha ? 4 : 3;
        final int alpha = lookup[0];
        final int blue = lookup[3];
        final int red = lookup[1];
        final int green = lookup[2];
        if (hasAlpha) {
            for (int pos = 0, el = 0; el < numElements; pos += skip, el++) {
                // BGRA
                outpixels[pos] = inpixels[pos + blue];
                outpixels[pos + 1] = inpixels[pos + green];
                outpixels[pos + 2] = inpixels[pos + red];
                outpixels[pos + 3] = inpixels[pos + alpha];
            }
        } else {
            for (int pos = 0, el = 0; el < numElements; pos += skip, el++) {
                // BGRA
                outpixels[pos] = inpixels[pos + blue];
                outpixels[pos + 1] = inpixels[pos + green];
                outpixels[pos + 2] = inpixels[pos + red];
            }
        }
        return raster;
    }

    public static Mat img2Mat(final BufferedImage crappyImage) {
        final int w = crappyImage.getWidth();
        final int h = crappyImage.getHeight();

        final int type = crappyImage.getType();

        switch (type) {
            case TYPE_3BYTE_BGR:
            case TYPE_INT_RGB: // 8-bit per channel packed into ints
            case TYPE_INT_BGR: // 8-bit per channel packed into ints, BGR order
            case TYPE_INT_ARGB:
            case TYPE_INT_ARGB_PRE:
            case TYPE_4BYTE_ABGR:
            case TYPE_4BYTE_ABGR_PRE: {
                final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
                if (!(dataBuffer instanceof DataBufferByte))
                    throw new IllegalArgumentException("BufferedImage should have a " + DataBufferByte.class.getSimpleName() + " but instead has a "
                            + dataBuffer.getClass().getSimpleName());
                final DataBufferByte bb = (DataBufferByte) dataBuffer;
                int[] lookup = null;
                switch (type) {
                    case TYPE_INT_RGB:
                        lookup = new int[] { -1, 0, 1, 2 };
                        break;
                    case TYPE_3BYTE_BGR:
                    case TYPE_INT_BGR:
                        lookup = new int[] { -1, 2, 1, 0 };
                        break;
                    case TYPE_INT_ARGB:
                    case TYPE_INT_ARGB_PRE:
                        lookup = new int[] { 0, 1, 2, 3 };
                        break;
                    case TYPE_4BYTE_ABGR:
                    case TYPE_4BYTE_ABGR_PRE:
                        lookup = new int[] { 0, 3, 2, 1 };
                        break;
                }
                final CvRaster raster = argbDataBufferByteToCvRaster(bb, h, w, lookup);
                return raster.toMat();
            }
            case TYPE_BYTE_GRAY: {
                final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
                if (!(dataBuffer instanceof DataBufferByte))
                    throw new IllegalArgumentException("BufferedImage should have a " + DataBufferByte.class.getSimpleName() + " but instead has a "
                            + dataBuffer.getClass().getSimpleName());
                final DataBufferByte bb = (DataBufferByte) dataBuffer;
                final byte[] srcdata = bb.getData();
                final Mat ret = new Mat(h, w, CvType.CV_8UC1);
                ret.put(0, 0, srcdata);
                return ret;
            }
            case TYPE_USHORT_GRAY: {
                final DataBuffer dataBuffer = crappyImage.getRaster().getDataBuffer();
                if (!(dataBuffer instanceof DataBufferUShort))
                    throw new IllegalArgumentException("BufferedImage should have a " + DataBufferUShort.class.getSimpleName() + " but instead has a "
                            + dataBuffer.getClass().getSimpleName());
                final DataBufferUShort bb = (DataBufferUShort) dataBuffer;
                final short[] srcdata = bb.getData();
                final Mat ret = new Mat(h, w, CvType.CV_16UC1);
                ret.put(0, 0, srcdata);
                return ret;
            }
            case TYPE_USHORT_565_RGB: // 16 bit total with 5 bit r, 6 bit g, 5 bit blue
            case TYPE_USHORT_555_RGB: // 15 bit (wtf?) with 5/5/5 r/g/b.
                throw new IllegalArgumentException("Cannot handle a BufferedImage with an alpha channel. Sorry. Ask nice maybe I'll fix it.");
            default:
                throw new IllegalArgumentException("Cannot extract pixels from a BufferedImage of type " + crappyImage.getType());
        }

        // public static Mat img2Mat(final BufferedImage in) {
        // Mat out;
        // byte[] data;
        // int r, g, b;
        //
        // final int w = in.getWidth();
        // final int h = in.getHeight();
        //
        // if (in.getType() == BufferedImage.TYPE_INT_RGB) {
        // out = new Mat(h, w, CvType.CV_8UC3);
        // data = new byte[h * 240 * (int) out.elemSize()];
        // final int[] dataBuff = in.getRGB(0, 0, 320, 240, null, 0, 320);
        // for (int i = 0; i < dataBuff.length; i++) {
        // data[i * 3] = (byte) ((dataBuff[i] >> 16) & 0xFF);
        // data[i * 3 + 1] = (byte) ((dataBuff[i] >> 8) & 0xFF);
        // data[i * 3 + 2] = (byte) ((dataBuff[i] >> 0) & 0xFF);
        // }
        // } else {
        // out = new Mat(240, 320, CvType.CV_8UC1);
        // data = new byte[320 * 240 * (int) out.elemSize()];
        // final int[] dataBuff = in.getRGB(0, 0, 320, 240, null, 0, 320);
        // for (int i = 0; i < dataBuff.length; i++) {
        // r = (byte) ((dataBuff[i] >> 16) & 0xFF);
        // g = (byte) ((dataBuff[i] >> 8) & 0xFF);
        // b = (byte) ((dataBuff[i] >> 0) & 0xFF);
        // data[i] = (byte) ((0.21 * r) + (0.71 * g) + (0.07 * b)); // luminosity
        // }
        // }
        // out.put(0, 0, data);
        // return out;
        // }
    }

    public static void print(final String prefix, final Mat im) {
        System.out.println(prefix + " { depth=(" + CvType.ELEM_SIZE(im.type()) + ", " + im.depth() + "), channels=" + im.channels() + " HxW="
                + im.height() + "x" + im.width() + " }");
    }

    public static class DumbPoint implements Point {
        private final double r;
        private final double c;

        public DumbPoint(final double r, final double c) {
            this.r = r;
            this.c = c;
        }

        @Override
        public double getRow() {
            return r;
        }

        @Override
        public double getCol() {
            return c;
        }
    }

    static public Point closest(final Point x, final double polarx, final double polary) {
        // Here we use the description for the perpendicularDistance.
        // if we translate X0 to the origin then Xi' (defined as
        // Xi translated by X0) will be at |P| - (P.X0)/|P| (which
        // is the signed magnitude of the X0 - Xi where the sign will
        // be positive if X0 X polar(P) is positive and negative
        // otherwise (that is, if X0 is on the "lower" side of the polar
        // line described by P)) along P itself. So:
        //
        // Xi' = (|P| - (P.X0)/|P|) Pu = (|P| - (P.X0)/|P|) P/|P|
        // = (1 - (P.X0)/|P|^2) P (where Pu is the unit vector in the P direction)
        //
        // then we can translate it back by X0 so that gives:
        //
        // Xi = (1 - (P.X0)/|P|^2) P + X0 = c P + X0
        // where c = (1 - (P.X0)/|P|^2)
        final double Pmagsq = (polarx * polarx) + (polary * polary);
        final double PdotX0 = (x.getRow() * polary) + (x.getCol() * polarx);

        final double c = (1.0 - (PdotX0 / Pmagsq));
        return new DumbPoint((c * polary) + x.getRow(), (c * polarx) + x.getCol());
    }

    public static void drawCircle(final Point p, final Mat ti, final Color color) {
        drawCircle(p, ti, color, 10);
    }

    public static void drawCircle(final int row, final int col, final Mat ti, final Color color) {
        drawCircle(row, col, ti, color, 10);
    }

    public static void drawCircle(final Point p, final Mat ti, final Color color, final int radius) {
        Imgproc.circle(ti, new org.opencv.core.Point(((int) (p.getCol() + 0.5)) - radius, ((int) (p.getRow() + 0.5)) - radius),
                radius, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static void drawCircle(final int row, final int col, final Mat ti, final Color color, final int radius) {
        Imgproc.circle(ti, new org.opencv.core.Point(((int) (col + 0.5)) - radius, ((int) (row + 0.5)) - radius),
                radius, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static void drawCircle(final int row, final int col, final Graphics2D g, final Color color, final int radius) {
        g.setColor(color);

        g.drawOval(((int) (col + 0.5)) - radius,
                ((int) (row + 0.5)) - radius,
                2 * radius, 2 * radius);

    }

    public static void drawCircle(final int row, final int col, final Graphics2D g, final Color color) {
        drawCircle(row, col, g, color, 10);
    }

    public static void drawCircle(final Point p, final Graphics2D g, final Color color) {
        drawCircle((int) p.getRow(), (int) p.getCol(), g, color, 10);
    }

    public static void drawBoundedPolarLine(final Point bound1, final Point bound2, final double r, final double c, final Mat ti, final Color color) {
        drawLine(closest(bound1, c, r), closest(bound2, c, r), ti, color);
    }

    public static void drawLine(final Point p1, final Point p2, final Mat ti, final Color color) {
        Imgproc.line(ti, new org.opencv.core.Point(p1.getCol(), p1.getRow()),
                new org.opencv.core.Point(p2.getCol(), p2.getRow()),
                new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static void drawLine(final Point p1, final Point p2, final Graphics2D g, final Color color) {
        g.setColor(color);
        g.drawLine((int) (p1.getCol() + 0.5), (int) (p1.getRow() + 0.5), (int) (p2.getCol() + 0.5), (int) (p2.getRow() + 0.5));
    }

    static public void drawPolarLine(final double r, final double c, final Mat ti, final Color color) {
        drawPolarLine(r, c, ti, color, 0, 0, ti.rows() - 1, ti.cols() - 1);
    }

    static public void drawPolarLine(final double r, final double c, final Mat ti, final Color color,
            final int boundingr1, final int boundingc1, final int boundingr2, final int boundingc2) {
        drawPolarLine(r, c, ti, color, boundingr1, boundingc1, boundingr2, boundingc2, 0, 0);
    }

    static public void drawPolarLine(final double r, final double c, final Mat ti, final Color color,
            int boundingr1, int boundingc1, int boundingr2, int boundingc2,
            final int translater, final int translatec) {
        int tmpd;
        if (boundingr1 > boundingr2) {
            tmpd = boundingr1;
            boundingr1 = boundingr2;
            boundingr2 = tmpd;
        }

        if (boundingc1 > boundingc2) {
            tmpd = boundingc1;
            boundingc1 = boundingc2;
            boundingc2 = tmpd;
        }

        // a polar line represented by r,c is a perpendicular to
        // the line from the origin to the point r,c. The line
        // from the origin to this point in rad,theta is given
        // by:
        //
        // rad = sqrt(r^2 + c^2)
        // theta = tan^-1(r/c)
        // (where theta is measured from the top of the
        // image DOWN to the point r,c)
        //
        // anyway - the line is represented by:
        // x cos(theta) + y sin (theta) = r

        final double rad = Math.sqrt((r * r) + (c * c));

        // we need to find the endpoints of the line:
        int r1, c1, r2, c2;

        // lets remove the simple possiblities
        if (c == 0.0) {
            r1 = r2 = (int) (rad + 0.5);
            c1 = boundingc1;
            c2 = boundingc2;
        } else if (r == 0.0) {
            c1 = c2 = (int) (rad + 0.5);
            r1 = boundingr1;
            r2 = boundingr2;
        } else {
            final double sintheta = r / rad;
            final double costheta = c / rad;

            // x cos th + y sin th = r =>
            // x (xc/r) + y (yc/r) = r (by definition of sin and cos) =>
            // x xc + y yc = r^2 =>
            // X.Xc = r^2 - (no duh!)

            // find the points at the boundaries

            // where does the line intersect the left/right boundary
            // bc costh + ir sinth = r =>
            //
            // r - bc costh
            // ir = -------------
            // sinth
            //
            final double leftIntersetingRow = (rad - ((boundingc1) * costheta)) / sintheta;
            final double rightIntersetingRow = (rad - ((boundingc2) * costheta)) / sintheta;

            // where does the line intersect the top/bottom boundary
            // ic costh + br sinth = r =>
            //
            // r - br sinth
            // ic = -------------
            // costh
            //
            final double topIntersectingCol = (rad - ((boundingr1) * sintheta)) / costheta;
            final double botIntersectingCol = (rad - ((boundingr2) * sintheta)) / costheta;

            // now, which pair works the best
            c1 = r1 = -1;
            if (leftIntersetingRow >= boundingr1 && leftIntersetingRow <= boundingr2) {
                c1 = boundingc1;
                r1 = (int) (leftIntersetingRow + 0.5);
            } else if (topIntersectingCol >= boundingc1 && topIntersectingCol <= boundingc2) {
                c1 = boundingr1;
                r1 = (int) (topIntersectingCol + 0.5);
            } else if (rightIntersetingRow >= boundingr1 && rightIntersetingRow <= boundingr2) {
                c1 = boundingc2;
                r1 = (int) (rightIntersetingRow + 0.5);
            } else if (botIntersectingCol >= boundingc1 && botIntersectingCol <= boundingc2) {
                c1 = boundingr2;
                r1 = (int) (botIntersectingCol + 0.5);
            }

            if (c1 == -1 && r1 == -1) // no part of the line intersects the box
                // {
                // System.out.println( " line " + r + "," + c + " does not intesect " +
                // boundingr1 + "," + boundingc1 + "," + boundingr2 + "," + boundingc2);
                return;
            // }

            // now search in the reverse direction for the other point
            c2 = r2 = -1;
            if (botIntersectingCol >= boundingc1 && botIntersectingCol <= boundingc2) {
                c2 = boundingr2;
                r2 = (int) (botIntersectingCol + 0.5);
            } else if (rightIntersetingRow >= boundingr1 && rightIntersetingRow <= boundingr2) {
                c2 = boundingc2;
                r2 = (int) (rightIntersetingRow + 0.5);
            } else if (topIntersectingCol >= boundingc1 && topIntersectingCol <= boundingc2) {
                c2 = boundingr1;
                r2 = (int) (topIntersectingCol + 0.5);
            } else if (leftIntersetingRow >= boundingr1 && leftIntersetingRow <= boundingr2) {
                c2 = boundingc1;
                r2 = (int) (leftIntersetingRow + 0.5);
            }

            // now, the two points should not be the same ... but anyway
        }

        Imgproc.line(ti, new org.opencv.core.Point(c1 + translatec, r1 + translater),
                new org.opencv.core.Point(c2 + translatec, r2 + translater),
                new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
    }

    public static BufferedImage dbgImage = null;

    public static Graphics2D wrap(final CvRaster cvraster) {
        if (cvraster.channels != 1 && CvType.depth(cvraster.type) != CvType.CV_8U)
            throw new IllegalArgumentException("can only get Graphics2D for an 8-bit CvRaster with 1 channel. Was passed " + cvraster);
        final DataBufferByte db = new DataBufferByte((byte[]) cvraster.data, cvraster.rows * cvraster.cols);
        final WritableRaster raster = Raster.createInterleavedRaster(db, cvraster.cols, cvraster.rows, cvraster.cols, 1, new int[] { 0 },
                new java.awt.Point(0, 0));
        final BufferedImage bi = new BufferedImage(Utils.grayColorModel, raster, false, null);
        dbgImage = bi;
        return bi.createGraphics();
    }

}
