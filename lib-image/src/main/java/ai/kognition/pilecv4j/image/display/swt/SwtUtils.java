package ai.kognition.pilecv4j.image.display.swt;

import java.util.stream.IntStream;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class SwtUtils {
    private static Display theDisplay = null;

    public static synchronized Display getDisplay() {
        if(theDisplay == null) {
            try {
                startSwt();
            } catch(final InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
        return theDisplay;
    }

    private static synchronized void startSwt() throws InterruptedException {
        ImageDisplay.syncExec(() -> {
            theDisplay = new Display();

            ImageDisplay.addEventPollingRunnable(() -> {
                ImageDisplay.syncExec(() -> {
                    while(theDisplay.readAndDispatch());
                });
            });
        });
    }

    public static synchronized void stopSwt() throws InterruptedException {
        ImageDisplay.syncExec(() -> {
            theDisplay.syncExec(() -> {
                if(theDisplay != null && !theDisplay.isDisposed())
                    theDisplay.dispose();
            });
        });
    }

    private static RGB[] grayscalePaletteColors = new RGB[256];
    static {
        IntStream.range(0, 256).forEach(i -> grayscalePaletteColors[i] = new RGB(i, i, i));
    }

    public static ImageData convertToDisplayableSWT(final Mat toUse) {
        try(CvMat displayable = ImageDisplay.displayable(toUse)) {
            return convertToSWT(displayable);
        }
    }

    public static ImageData convertToSWT(final Mat image) {
        final int type = image.type();
        final int inChannels = image.channels();
        final int cvDepth = CvType.depth(type);
        if(cvDepth != CvType.CV_8U && cvDepth != CvType.CV_8S)
            throw new IllegalArgumentException("Cannot convert Mat to SWT image with elements larger than a byte yet.");

        final int width = image.cols();
        final int height = image.rows();

        Mat toUse = image;

        final PaletteData palette;
        try(CvMat alt = new CvMat();) {
            switch(inChannels) {
                case 1:
                    palette = new PaletteData(grayscalePaletteColors);
                    break;
                case 3:
                    palette = new PaletteData(0x0000FF, 0x00FF00, 0xFF0000);
                    break;
                case 4:
                    // hack for B&W pngs
                    // palette = new PaletteData(0xFF0000, 0xFF0000, 0xFF0000);
                    Imgproc.cvtColor(image, alt, Imgproc.COLOR_BGRA2BGR);
                    toUse = alt;
                    palette = new PaletteData(0x0000FF, 0x00FF00, 0xFF0000);
                    break;
                // throw new IllegalArgumentException("Can't handle alpha channel yet.");
                default:
                    throw new IllegalArgumentException("Can't handle an image with " + inChannels + " channels");
            }

            final int elemSize = CvType.ELEM_SIZE(toUse.type());

            final ImageData id = new ImageData(width, height, elemSize * 8, palette, 1, new byte[width * height * elemSize]);
            CvMat.rasterAp(toUse, raster -> raster.underlying().get(id.data));
            return id;
        }
    }

}
