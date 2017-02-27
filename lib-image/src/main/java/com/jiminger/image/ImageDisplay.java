package com.jiminger.image;
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
import java.lang.reflect.Method;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import com.jiminger.util.LibraryLoader;

public class ImageDisplay {
    static {
        LibraryLoader.init();
    }

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

        try (Guard g = new Guard(display = new Display());
                Guard g2 = new Guard(shell = new Shell(display))) {
            final FileDialog dialog = new FileDialog(shell, SWT.OPEN);
            dialog.setText("Open an image file or cancel");
            final String string = dialog.open();
            if (string != null)
                iioimage = ImageFile.readImageFile(string);
        }

        if (iioimage != null)
            showImage(iioimage);
    }

    public static void showImage(final BufferedImage iioimage) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final JPanel p = new ScrollImageTest(iioimage);
                final JFrame f = new JFrame();
                f.setContentPane(p);
                f.setSize(400, 300);
                f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                f.setVisible(true);
            }
        });
    }

    public static class ScrollImageTest extends JPanel {
        private static final long serialVersionUID = 1L;

        public ScrollImageTest(final BufferedImage image) {
            final JPanel canvas = new JPanel() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(final Graphics g) {
                    super.paintComponent(g);
                    g.drawImage(image, 0, 0, null);
                }
            };
            canvas.add(new JButton("Currently I do nothing"));
            canvas.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
            final JScrollPane sp = new JScrollPane(canvas);
            setLayout(new BorderLayout());
            add(sp, BorderLayout.CENTER);
        }

    }
}
