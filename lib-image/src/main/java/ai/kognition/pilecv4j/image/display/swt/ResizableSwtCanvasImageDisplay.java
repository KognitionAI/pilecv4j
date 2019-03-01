package ai.kognition.pilecv4j.image.display.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Layout;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import net.dempsy.util.Functional;
import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.Utils;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class ResizableSwtCanvasImageDisplay extends SwtCanvasImageDisplay {

    private Size parentBounds = null;

    public ResizableSwtCanvasImageDisplay() {}

    public ResizableSwtCanvasImageDisplay(final Composite parent, final Runnable closeCallback, final KeyPressCallback kpCallback,
        final SelectCallback selectCallback) {

        attach(parent, closeCallback, kpCallback, selectCallback);
    }

    public Canvas attach(final Composite parent) {
        return attach(parent, null, null, null);
    }

    private void updateBounds() {
        final Rectangle curBounds = parent.getBounds();
        parentBounds = new Size(curBounds.width, curBounds.height);
    }

    public Canvas attach(final Composite parentx, final Runnable closeCallback, final KeyPressCallback kpCallback,
        final SelectCallback selectCallback) {

        super.setup(new Canvas(parentx, SWT.NO_BACKGROUND), closeCallback, kpCallback, selectCallback);
        if(parent.isVisible()) {
            updateBounds();
        }

        final Display display = SwtUtils.getDisplay();

        ImageDisplay.syncExec(() -> {
            canvas.addListener(SWT.Paint, e -> {
                try (final CvMat lcurrentImageMat = Functional.applyIfExistsAndReturnResult(currentImageRef, CvMat::shallowCopy);) {
                    if(lcurrentImageMat != null) {
                        if(parent.isVisible()) {
                            if(parentBounds == null) {
                                updateBounds();
                            }
                            final Image lcurrentImage = new Image(display, convertToDisplayableSWT(lcurrentImageMat));
                            try (QuietCloseable qc = () -> lcurrentImage.dispose();) {
                                final int x = ((int)parentBounds.width - lcurrentImage.getBounds().width) >>> 1;
                                final int y = ((int)parentBounds.height - lcurrentImage.getBounds().height) >>> 1;
                                canvas.setBounds(x, y, lcurrentImage.getBounds().width, lcurrentImage.getBounds().height);

                                final GC gc = e.gc;
                                gc.drawImage(lcurrentImage, 0, 0);

                                // get the bounds of the image.
                                final Rectangle rect = lcurrentImage.getBounds();

                                // get the bounds of the canvas
                                final Rectangle client = canvas.getClientArea();

                                // there may be a margin between the image and the edge of the canvas
                                // if the canvas is bigger than the image.
                                final int marginWidth = client.width - rect.width;
                                if(marginWidth > 0) {
                                    gc.fillRectangle(rect.width, 0, marginWidth, client.height);
                                }
                                final int marginHeight = client.height - rect.height;
                                if(marginHeight > 0) {
                                    gc.fillRectangle(0, rect.height, client.width, marginHeight);
                                }

                                // if we haven't packed the display layout since we either never have
                                // or the image changed size, we need to do that now.
                                if(!alreadySized.get()) {

                                    final Layout layout = parent.getLayout();
                                    if(layout instanceof GridLayout) {
                                        final GridData layoutData = new GridData(GridData.FILL_BOTH);
                                        layoutData.widthHint = parent.getBounds().width;
                                        layoutData.heightHint = parent.getBounds().height;
                                        canvas.setLayoutData(layoutData);
                                    } else if(layout instanceof RowLayout) {
                                        final RowData layoutData = new RowData(parent.getBounds().width, parent.getBounds().height);
                                        canvas.setLayoutData(layoutData);
                                    }

                                    canvas.layout(true, true);
                                    parent.layout(true, true);
                                    parent.requestLayout();
                                    // shell.pack(true);
                                    alreadySized.set(true);
                                }
                            }
                        }
                    }
                }
            });

            parent.addListener(SWT.Resize, e -> {
                updateBounds();
            });
        });

        return canvas;
    }

    @Override
    public org.opencv.core.Point canvasLocationToImageLocation(final int x, final int y) {
        // TODO: fix this
        return new org.opencv.core.Point(x, y);
    }

    public ImageData convertToDisplayableSWT(final Mat image) {
        try (final CvMat toDisplay = new CvMat();) {
            Imgproc.resize(image, toDisplay, Utils.preserveAspectRatio(image, parentBounds));
            return SwtUtils.convertToDisplayableSWT(toDisplay);
        }
    }
}
