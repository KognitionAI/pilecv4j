package ai.kognition.pilecv4j.image;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.uncheck;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class CvMatWithColorInformation extends CvMat {
    public final boolean iCC;
    public final boolean isLinearRGBspace;
    public final boolean isGray;
    public final boolean isSRgb;
    public final int colorSpaceType;

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

    CvMatWithColorInformation(final CvMat mat, final BufferedImage im) {
        CvMat.reassign(this, mat);

        final ColorSpace colorSpace = im.getColorModel().getColorSpace();
        isLinearRGBspace = isLinearRGBspace(colorSpace);

        iCC = (ICC_ColorSpace.class.isAssignableFrom(colorSpace.getClass()));
        isGray = (colorSpace.getType() == ColorSpace.TYPE_GRAY);
        isSRgb = colorSpace == ColorSpace.getInstance(ColorSpace.CS_sRGB);
        colorSpaceType = colorSpace.getType();
    }

    public static boolean isLinearRGBspace(final ColorSpace colorSpace) {
        return (Boolean)(uncheck(() -> isLinearRGBspaceMethod.invoke(null, colorSpace)));
    }

    @Override
    public CvMatWithColorInformation returnMe() {
        return this;
    }

    @Override
    public String toString() {
        return super.toString() + " [ ColorSpace: " + csTypeName.get(colorSpaceType) + ", is ICC: " + iCC + ", is Linear RGB: " + isLinearRGBspace
                + ", is Gray:" + isGray + ", sRGB CS:" + isSRgb + "]";
    }
}
