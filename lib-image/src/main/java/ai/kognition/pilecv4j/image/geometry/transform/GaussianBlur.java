package ai.kognition.pilecv4j.image.geometry.transform;

import org.opencv.core.Core;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvMat;

/**
 * A <a href="https://docs.opencv.org/3.3.1/d4/d86/group__imgproc__filter.html#gaabe8c836e97159a9193fb0b11ac52cf1">Gaussian blur</a> is a <a
 * href="http://northstar-www.dartmouth.edu/doc/idl/html_6.2/Filtering_an_Imagehvr.html">low-pass filter</a>. It essentially makes changes more gradual which
 * has the effect of reducing edges. It is often used in image processing to reduce detail or smooth an image through the application of a
 * <a href="https://shapeofdata.wordpress.com/2013/07/23/gaussian-kernels/">Gaussian kernel</a>. This essentially weights
 * the blurring of the pixels in an image using a <a href="http://mathworld.wolfram.com/GaussianFunction.html">Gaussian function</a>.
 * <p>
 * This class implements the {@link Imgproc} GaussianBlur method to return a CvMat that has been transformed by a Gaussian blur with the user's specifications.
 */

public class GaussianBlur {

    /**
     * The list of <a href="https://docs.opencv.org/3.3.1/d2/de8/group__core__array.html#ga209f2f4869e304c82d07739337eae7c5">BorderTypes</a> includes all the
     * options for pixel extrapolation, which are ways of predicting pixel values, that are in this case used at image borders. This is necessary because the
     * Gaussian kernel uses surrounding pixels to transform a given region.
     */
    public enum BorderTypes {
        BORDER_CONSTANT(Core.BORDER_CONSTANT),
        BORDER_REPLICATE(Core.BORDER_REPLICATE),
        BORDER_REFLECT(Core.BORDER_REFLECT),
        BORDER_WRAP(Core.BORDER_WRAP),
        BORDER_REFLECT_101(Core.BORDER_REFLECT_101),
        BORDER_TRANSPARENT(Core.BORDER_TRANSPARENT),
        BORDER_REFLECT101(Core.BORDER_REFLECT101),
        BORDER_DEFAULT(Core.BORDER_DEFAULT),
        BORDER_ISOLATED(Core.BORDER_ISOLATED);

        private final int value;

        private BorderTypes(final int value) {
            this.value = value;
        }
    }

    private final Size kernelSize;
    private final double sigmaX;
    private final double sigmaY;
    private final int borderType;

    /**
     * Creates a <a href="https://docs.opencv.org/3.3.1/d4/d86/group__imgproc__filter.html#gaabe8c836e97159a9193fb0b11ac52cf1">GaussianBlur</a> using the given
     * specifications to construct a <a href="https://docs.opencv.org/3.3.1/d4/d86/group__imgproc__filter.html#gac05a120c1ae92a6060dd0db190a61afa">Gaussian
     * kernel</a>.
     *
     * @param kernelSize controls the dimensions of the Gaussian kernel. The width and height should be positive and odd
     * @param sigmaX half of the distance between the points at half the Gaussian function's maximum value in the x direction
     * @param sigmaY half of the distance between the points at half the Gaussian function's maximum value in the y direction
     * @param borderType the border extrapolation method to be used, see {@link BorderTypes}
     */
    public GaussianBlur(final Size kernelSize, final double sigmaX, final double sigmaY, final int borderType) {
        this.kernelSize = kernelSize;
        this.sigmaX = sigmaX;
        this.sigmaY = sigmaY;
        this.borderType = borderType;
    }

    /**
     * Uses the {@link Imgproc} GaussianBlur method to smooth the image using the specifications given in the constructor and returns a CvMat of the transformed
     * image, instead of void like the Imgproc method.
     *
     * @param mat CvMat of image to be blurred
     *
     * @return a CvMat of the blurred image
     */
    public CvMat gaussianBlur(final CvMat mat) {
        Imgproc.GaussianBlur(mat, mat, kernelSize, sigmaX, sigmaY, borderType);
        return mat.returnMe();
    }
}
