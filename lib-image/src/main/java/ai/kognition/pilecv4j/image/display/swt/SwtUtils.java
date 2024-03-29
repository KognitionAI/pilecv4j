/*
 * Copyright 2022 Jim Carroll
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.kognition.pilecv4j.image.display.swt;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
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

    private static boolean loaded = false;

    public static synchronized void loadNative() {
        if(!loaded) {
            // test if the jar is already on the classpath or is shadded
            boolean onClasspath = false;
            try {
                Class.forName("org.eclipse.swt.widgets.Display");
                onClasspath = true;
            } catch(final ClassNotFoundException cnfe) {
                onClasspath = false;
            }
            if(!onClasspath) {
                final String osName = System.getProperty("os.name").toLowerCase();
                final String osArch = System.getProperty("os.arch").toLowerCase();
                final String swtFileNameOsPart = osName.contains("win") ? "win32"
                    : osName.contains("mac") ? "macosx" : osName.contains("linux") || osName.contains("nix") ? "linux_gtk" : ""; // throw new
                                                                                                                                 // RuntimeException("Unknown
                                                                                                                                 // OS name: "+osName)

                final String swtFileNameArchPart = osArch.contains("64") ? "x64" : "x86";
                final String swtFileName = "swt_" + swtFileNameOsPart + "_" + swtFileNameArchPart + ".jar";

                try {
                    final URLClassLoader classLoader = (URLClassLoader)SwtUtils.class.getClassLoader();
                    final Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    addUrlMethod.setAccessible(true);

                    final URL swtFileUrl = new URL("file:" + swtFileName);
                    addUrlMethod.invoke(classLoader, swtFileUrl);
                } catch(final Exception e) {
                    throw new RuntimeException("Unable to add the SWT jar to the class path: " + swtFileName, e);
                }
            }
        } else
            loaded = true;
    }

    public static synchronized Display getDisplay() {
        if(theDisplay == null) {
            startSwt();
        }
        return theDisplay;
    }

    private static synchronized void startSwt() {
        ImageDisplay.syncExec(() -> {
            theDisplay = new Display();

            ImageDisplay.addEventPollingRunnable(() -> {
                ImageDisplay.syncExec(() -> {
                    while(theDisplay.readAndDispatch());
                });
            });
        });
    }

    public static synchronized void stopSwt() {
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
            CvMat.bulkAccess(toUse, raster -> raster.get(id.data));
            return id;
        }
    }

}
