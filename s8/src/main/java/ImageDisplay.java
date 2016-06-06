/******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/


/*
 * Canvas example snippet: scroll an image (flicker free, no double buffering)
 *
 * For a list of all SWT example snippets see
 * http://www.eclipse.org/swt/snippets/
 */
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;

import com.jiminger.image.ImageFile;

public class ImageDisplay {
    
    public static void main (String [] args) 
    throws Exception
    {
        Display display = new Display ();
        Shell shell = new Shell (display);
        shell.setLayout(new FillLayout());
        Image originalImage = null;
        FileDialog dialog = new FileDialog (shell, SWT.OPEN);
        dialog.setText ("Open an image file or cancel");
        String string = dialog.open ();

        BufferedImage iioimage = null;
        if (string != null) 
        {
            iioimage = ImageFile.readImageFile(string);
            originalImage = new Image(display,convertToSWT(iioimage));
        }
        if (originalImage == null) {
            int width = 150, height = 200;
            originalImage = new Image (display, width, height);
            GC gc = new GC (originalImage);
            gc.fillRectangle (0, 0, width, height);
            gc.drawLine (0, 0, width, height);
            gc.drawLine (0, height, width, 0);
            gc.drawText ("Default Image", 10, 10);
            gc.dispose ();
        }
        final Image image = originalImage;
        final Point origin = new Point (0, 0);
        final Canvas canvas = new Canvas (shell, SWT.NO_BACKGROUND |
                SWT.NO_REDRAW_RESIZE | SWT.V_SCROLL | SWT.H_SCROLL);
        WritableRaster raster = iioimage.getRaster();
        canvas.addMouseListener( new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {}
			
			@Override
			public void mouseDown(MouseEvent e) {
		        Object pixelArray = raster.getDataElements(e.x, e.y, (Object)null);

            	System.out.println("(" + e.x + ", " + e.y + ")" + Arrays.toString((byte[]) pixelArray));
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {}
		});
        final ScrollBar hBar = canvas.getHorizontalBar ();
        hBar.addListener (SWT.Selection, new Listener () {
            public void handleEvent (Event e) {
                int hSelection = hBar.getSelection ();
                int destX = -hSelection - origin.x;
                Rectangle rect = image.getBounds ();
                canvas.scroll (destX, 0, 0, 0, rect.width, rect.height, false);
                origin.x = -hSelection;
            }
        });
        final ScrollBar vBar = canvas.getVerticalBar ();
        vBar.addListener (SWT.Selection, new Listener () {
            public void handleEvent (Event e) {
                int vSelection = vBar.getSelection ();
                int destY = -vSelection - origin.y;
                Rectangle rect = image.getBounds ();
                canvas.scroll (0, destY, 0, 0, rect.width, rect.height, false);
                origin.y = -vSelection;
            }
        });
        canvas.addListener (SWT.Resize,  new Listener () {
            public void handleEvent (Event e) {
                Rectangle rect = image.getBounds ();
                Rectangle client = canvas.getClientArea ();
                hBar.setMaximum (rect.width);
                vBar.setMaximum (rect.height);
                hBar.setThumb (Math.min (rect.width, client.width));
                vBar.setThumb (Math.min (rect.height, client.height));
                int hPage = rect.width - client.width;
                int vPage = rect.height - client.height;
                int hSelection = hBar.getSelection ();
                int vSelection = vBar.getSelection ();
                if (hSelection >= hPage) {
                    if (hPage <= 0) hSelection = 0;
                    origin.x = -hSelection;
                }
                if (vSelection >= vPage) {
                    if (vPage <= 0) vSelection = 0;
                    origin.y = -vSelection;
                }
                canvas.redraw ();
            }
        });
        canvas.addListener (SWT.Paint, new Listener () {
            public void handleEvent (Event e) {
                GC gc = e.gc;
                gc.drawImage (image, origin.x, origin.y);
                Rectangle rect = image.getBounds ();
                Rectangle client = canvas.getClientArea ();
                int marginWidth = client.width - rect.width;
                if (marginWidth > 0) {
                    gc.fillRectangle (rect.width, 0, marginWidth, client.height);
                }
                int marginHeight = client.height - rect.height;
                if (marginHeight > 0) {
                    gc.fillRectangle (0, rect.height, client.width, marginHeight);
                }
            }
        });
//        shell.setSize (200, 150);
        shell.open ();
        while (!shell.isDisposed ()) {
            if (!display.readAndDispatch ()) display.sleep ();
        }
        originalImage.dispose();
        display.dispose ();
    }
    
    static ImageData convertToSWT(BufferedImage bufferedImage) {
        if (bufferedImage.getColorModel() instanceof DirectColorModel) {
            DirectColorModel colorModel = (DirectColorModel)bufferedImage.getColorModel();
            PaletteData palette = new PaletteData(colorModel.getRedMask(), colorModel.getGreenMask(), colorModel.getBlueMask());
            ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[3];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getPixel(x, y, pixelArray);
                    int pixel = palette.getPixel(new RGB(pixelArray[0], pixelArray[1], pixelArray[2]));
                    data.setPixel(x, y, pixel);
                }
            }       
            return data;        
        } else if (bufferedImage.getColorModel() instanceof IndexColorModel) {
            IndexColorModel colorModel = (IndexColorModel)bufferedImage.getColorModel();
            int size = colorModel.getMapSize();
            byte[] reds = new byte[size];
            byte[] greens = new byte[size];
            byte[] blues = new byte[size];
            colorModel.getReds(reds);
            colorModel.getGreens(greens);
            colorModel.getBlues(blues);
            RGB[] rgbs = new RGB[size];
            for (int i = 0; i < rgbs.length; i++) {
                rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF, blues[i] & 0xFF);
            }
            PaletteData palette = new PaletteData(rgbs);
            ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), colorModel.getPixelSize(), palette);
            data.transparentPixel = colorModel.getTransparentPixel();
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[1];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getPixel(x, y, pixelArray);
                    data.setPixel(x, y, pixelArray[0]);
                }
            }
            return data;
        } else if (bufferedImage.getColorModel() instanceof ComponentColorModel) {
            ComponentColorModel colorModel = (ComponentColorModel)bufferedImage.getColorModel();
            
            int pixelSize = (Arrays.stream(colorModel.getComponentSize()).anyMatch(cs -> cs > 8)) ? 24 : colorModel.getPixelSize();

            //ASSUMES: 3 BYTE BGR IMAGE TYPE

            PaletteData palette = new PaletteData(0x0000FF, 0x00FF00,0xFF0000);
            ImageData data = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), pixelSize, palette);

            //This is valid because we are using a 3-byte Data model with no transparent pixels
            data.transparentPixel = -1;

            WritableRaster raster = bufferedImage.getRaster();
            Object pixelArray = raster.getDataElements(0, 0, (Object)null);
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getDataElements(x, y, pixelArray);
                    int pixel = palette.getPixel(new RGB(colorModel.getRed(pixelArray), colorModel.getGreen(pixelArray), colorModel.getBlue(pixelArray)));
                    data.setPixel(x, y, pixel);
                }
            }
            return data;
        }
        return null;
    }
} 
