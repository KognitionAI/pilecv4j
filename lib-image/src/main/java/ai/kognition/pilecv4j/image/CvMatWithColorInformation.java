package ai.kognition.pilecv4j.image;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import ai.kognition.pilecv4j.image.CvRaster.Closer;

public class CvMatWithColorInformation extends CvMat {
    public final boolean iCC;
    public final boolean isLinearRGBspace;
    public final boolean isGray;
    public final boolean isSRgb;
    public final int colorSpaceType;
    public final int numColorModelChannels;

    private static Map<Integer, String> csTypeName = new HashMap<>();

    static {
        csTypeName.put(ColorSpace.TYPE_XYZ, "TYPE_XYZ");
        csTypeName.put(ColorSpace.TYPE_Lab, "TYPE_Lab");
        csTypeName.put(ColorSpace.TYPE_Luv, "TYPE_Luv");
        csTypeName.put(ColorSpace.TYPE_YCbCr, "TYPE_YCbCr");
        csTypeName.put(ColorSpace.TYPE_Yxy, "TYPE_Yxy");
        csTypeName.put(ColorSpace.TYPE_RGB, "TYPE_RGB");
        csTypeName.put(ColorSpace.TYPE_GRAY, "TYPE_GRAY");
        csTypeName.put(ColorSpace.TYPE_HSV, "TYPE_HSV");
        csTypeName.put(ColorSpace.TYPE_HLS, "TYPE_HLS");
        csTypeName.put(ColorSpace.TYPE_CMYK, "TYPE_CMYK");
        csTypeName.put(ColorSpace.TYPE_CMY, "TYPE_CMY");
        csTypeName.put(ColorSpace.TYPE_2CLR, "TYPE_2CLR");
        csTypeName.put(ColorSpace.TYPE_3CLR, "TYPE_3CLR");
        csTypeName.put(ColorSpace.TYPE_4CLR, "TYPE_4CLR");
        csTypeName.put(ColorSpace.TYPE_5CLR, "TYPE_5CLR");
        csTypeName.put(ColorSpace.TYPE_6CLR, "TYPE_6CLR");
        csTypeName.put(ColorSpace.TYPE_7CLR, "TYPE_7CLR");
        csTypeName.put(ColorSpace.TYPE_8CLR, "TYPE_8CLR");
        csTypeName.put(ColorSpace.TYPE_9CLR, "TYPE_9CLR");
        csTypeName.put(ColorSpace.TYPE_ACLR, "TYPE_ACLR");
        csTypeName.put(ColorSpace.TYPE_BCLR, "TYPE_BCLR");
        csTypeName.put(ColorSpace.TYPE_CCLR, "TYPE_CCLR");
        csTypeName.put(ColorSpace.TYPE_DCLR, "TYPE_DCLR");
        csTypeName.put(ColorSpace.TYPE_ECLR, "TYPE_ECLR");
        csTypeName.put(ColorSpace.TYPE_FCLR, "TYPE_FCLR");
        csTypeName.put(ColorSpace.CS_sRGB, "CS_sRGB");
        csTypeName.put(ColorSpace.CS_LINEAR_RGB, "CS_LINEAR_RGB");
        csTypeName.put(ColorSpace.CS_CIEXYZ, "CS_CIEXYZ");
        csTypeName.put(ColorSpace.CS_PYCC, "CS_PYCC");
        csTypeName.put(ColorSpace.CS_GRAY, "CS_GRAY");
    }

    private static final Method isLinearRGBspaceMethod = chain(uncheck(() -> ColorModel.class.getDeclaredMethod("isLinearRGBspace", ColorSpace.class)),
        m -> m.setAccessible(true));

    public static String colorSpaceTypeName(final int colorSpaceType) {
        final String ret = csTypeName.get(colorSpaceType);
        return ret == null ? "UNKNOWN" : ret;
    }

    CvMatWithColorInformation(final CvMat mat, final BufferedImage im) {
        CvMat.reassign(this, mat);

        final ColorModel cm = im.getColorModel();
        final ColorSpace colorSpace = cm.getColorSpace();
        isLinearRGBspace = isLinearRGBspace(colorSpace);

        iCC = (ICC_ColorSpace.class.isAssignableFrom(colorSpace.getClass()));
        isGray = (colorSpace.getType() == ColorSpace.TYPE_GRAY);
        isSRgb = colorSpace == ColorSpace.getInstance(ColorSpace.CS_sRGB);
        colorSpaceType = colorSpace.getType();
        numColorModelChannels = cm.getNumColorComponents();
    }

    public CvMat displayable() {
        try(final CvMat ret = CvMat.shallowCopy(this);
            final Closer c = new Closer();) {
            if(channels() > 4) {
                final List<Mat> channels = new ArrayList<>(channels());
                Core.split(this, channels);
                channels.forEach(m -> c.addMat(m));
                final List<Mat> sub = channels.subList(0, numColorModelChannels);
                Core.merge(sub, ret);
            } else if(channels() == 2 && isGray) {
                final List<Mat> channels = new ArrayList<>(channels());
                Core.split(this, channels);
                channels.forEach(m -> c.addMat(m));
                final List<Mat> newChannels = new ArrayList<>();
                final Mat gray = channels.get(0);
                for(int i = 0; i < 3; i++)
                    newChannels.add(c.addMat(CvMat.shallowCopy(gray)));
                newChannels.add(c.addMat(CvMat.shallowCopy(channels.get(1))));
                Core.merge(newChannels, ret);
            }

            if(ret.depth() == CvType.CV_32S) {
                final List<Mat> channels = new ArrayList<>(channels());
                Core.split(ret, channels);
                channels.forEach(m -> c.addMat(m));
                final List<Mat> newChannels = new ArrayList<>();
                for(final Mat ch: channels) {
                    final Mat newMat = c.addMat(new Mat());
                    Utils.bitwiseUnsignedRightShiftAndMask(ch, newMat, 16, 16);
                    newMat.convertTo(newMat, CvType.makeType(CvType.CV_16S, 1));
                    newChannels.add(newMat);
                }
                Core.merge(newChannels, ret);
            }
            return ret.returnMe();
        }
    }

    public static boolean isLinearRGBspace(final ColorSpace colorSpace) {
        return (Boolean)(uncheck(() -> isLinearRGBspaceMethod.invoke(null, colorSpace)));
    }

    @Override
    public String toString() {
        return super.toString() + " [ ColorSpace: " + csTypeName.get(colorSpaceType) + ", is ICC: " + iCC + ", is Linear RGB: " + isLinearRGBspace
            + ", is Gray:" + isGray + ", sRGB CS:" + isSRgb + "]";
    }
}
