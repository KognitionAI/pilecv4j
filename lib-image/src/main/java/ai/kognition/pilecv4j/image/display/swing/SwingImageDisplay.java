package ai.kognition.pilecv4j.image.display.swing;
/******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *****************************************************************************/

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
/*
 * Canvas example snippet: scroll an image (flicker free, no double buffering)
 *
 * For a list of all SWT example snippets see
 * http://www.eclipse.org/swt/snippets/
 */
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.opencv.core.Mat;

import net.dempsy.util.QuietCloseable;

import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.display.ImageDisplay;

public class SwingImageDisplay extends ImageDisplay {
    private static class Guard implements AutoCloseable {
        final Object d;

        public Guard(final Object d) throws Exception {
            this.d = d;
        }

        @Override
        public void close() throws Exception {
            final Method closeMethod = d.getClass().getDeclaredMethod("close");
            closeMethod.invoke(d);
        }
    }

    public static void main(final String[] args) throws Exception {
        Display display;
        Shell shell;
        BufferedImage iioimage = null;

        try(Guard g = new Guard(display = new Display());
            Guard g2 = new Guard(shell = new Shell(display))) {
            final FileDialog dialog = new FileDialog(shell, SWT.OPEN);
            dialog.setText("Open an image file or cancel");
            final String string = dialog.open();
            if(string != null)
                iioimage = ImageFile.readBufferedImageFromFile(string);
        }

        if(iioimage != null)
            showImage(iioimage);
    }

    public static QuietCloseable showImage(final BufferedImage iioimage) throws InvocationTargetException,
        InterruptedException {
        final AtomicReference<JFrame> frame = new AtomicReference<JFrame>(null);
        SwingUtilities.invokeAndWait(() -> {
            final JPanel p = new ScrollImagePanel(iioimage);
            final JFrame f = new JFrame();
            f.setContentPane(p);
            f.setSize(iioimage.getWidth(), iioimage.getHeight());
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            frame.set(f);
        });
        return () -> frame.get().dispose();
    }

    public static class ScrollImagePanel extends JPanel {
        private static final long serialVersionUID = 1L;

        public ScrollImagePanel(final BufferedImage image) {
            final JPanel canvas = new JPanel() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(final Graphics g) {
                    super.paintComponent(g);
                    g.drawImage(image, 0, 0, null);
                }
            };
            canvas.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            final JScrollPane sp = new JScrollPane(canvas);
            setLayout(new BorderLayout());
            add(sp, BorderLayout.CENTER);
        }
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
    }

    @Override
    public void update(final Mat toUpdate) {
        // TODO Auto-generated method stub
    }

    @Override
    public void waitUntilClosed() throws InterruptedException {
        // TODO Auto-generated method stub
    }

    @Override
    public void setCloseCallback(final Runnable closeCallback) {
        // TODO Auto-generated method stub

    }
}
