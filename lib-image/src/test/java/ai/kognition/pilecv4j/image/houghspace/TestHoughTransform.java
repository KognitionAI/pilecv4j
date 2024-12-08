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

package ai.kognition.pilecv4j.image.houghspace;

import static ai.kognition.pilecv4j.image.Operations.BOVERLAY;
import static ai.kognition.pilecv4j.image.Operations.COVERLAY;
import static ai.kognition.pilecv4j.image.Operations.GOVERLAY;
import static ai.kognition.pilecv4j.image.Operations.ROVERLAY;
import static ai.kognition.pilecv4j.image.Operations.YOVERLAY;
import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opencv.core.CvType;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import ai.kognition.pilecv4j.image.Closer;
import ai.kognition.pilecv4j.image.CvMat;
import ai.kognition.pilecv4j.image.ImageFile;
import ai.kognition.pilecv4j.image.Operations;
import ai.kognition.pilecv4j.image.Operations.GradientImages;
import ai.kognition.pilecv4j.image.geometry.LineSegment;
import ai.kognition.pilecv4j.image.geometry.LineSegment.Direction;
import ai.kognition.pilecv4j.image.geometry.SimplePoint;
import ai.kognition.pilecv4j.image.houghspace.Transform.HoughSpaceEntry;

public class TestHoughTransform {

    public static final double WIDTH = 146.0;
    public static final double HEIGHT = 118.0;
    public static final double thigh = 200;
    public static final int tlowpct = 50;
    public static final int kernelSize = 3;
    public static final double quantFactor = 7.0;
    public static final int houghThreshold = 150;

    public static final int cc = (int)(146 + (WIDTH / 2.0));
    public static final int cr = (int)(948 + (HEIGHT / 2.0));

    public static final double clusterFactor = 0.2;

    @Rule public TemporaryFolder outputDir = new TemporaryFolder();

    String testFileName = "img00047.jp2";

    @Test
    public void testTransformOnMovieImage() throws Exception {

        try(final InputStream is = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(testFileName));
            final Closer c = new Closer();) {

            // ======================================================================
            // prepare the input image
            // copy the file into the temp folder
            final File rootDir = outputDir.newFolder();
            // final File rootDir = new File("/tmp");
            final String testFile = new File(rootDir, testFileName).getAbsolutePath();

            try(OutputStream os = new BufferedOutputStream(new FileOutputStream(testFile))) {
                IOUtils.copyLarge(is, os);
            }
            // ======================================================================

            final Model sm = new SegmentModel(Arrays.asList(
                new LineSegment(new SimplePoint(0, 0), new SimplePoint(0, WIDTH), Direction.LEFT),
                new LineSegment(new SimplePoint(0.0, WIDTH), new SimplePoint(HEIGHT, WIDTH), Direction.LEFT),
                new LineSegment(new SimplePoint(HEIGHT, WIDTH), new SimplePoint(HEIGHT, 0), Direction.LEFT),
                new LineSegment(new SimplePoint(HEIGHT, 0), new SimplePoint(0, 0), Direction.LEFT)));

            final CvMat origImage = c.add(ImageFile.readMatFromFile(testFile));
            final CvMat sprocketInfoTiledImage = c.add(new CvMat(origImage.rows(), origImage.cols(), CvType.CV_8UC1));

            // convert to gray scale
            final CvMat grayImage = c.add(Operations.convertToGray(origImage));
            Imgproc.GaussianBlur(grayImage, grayImage, new Size(kernelSize + 2, kernelSize + 2), 0.0);
            ImageFile.writeImageFile(grayImage, new File(rootDir, "tmpgray.bmp").getAbsolutePath());

            // find gradient image
            final GradientImages gis = c.add(Operations.gradient(grayImage, kernelSize));
            final CvMat gradientDirRaster = gis.gradientDir;
            ImageFile.writeImageFile(gis.gradientDir, new File(rootDir, "tmpgradDir.bmp").getAbsolutePath());
            ImageFile.writeImageFile(gis.dx, new File(rootDir, "tmpdx.bmp").getAbsolutePath());
            ImageFile.writeImageFile(gis.dy, new File(rootDir, "tmpdy.bmp").getAbsolutePath());

            // edge detection
            final double tlow = (tlowpct / 100.0) * thigh;
            final CvMat edgeRaster = c.add(Operations.canny(gis, tlow, thigh));
            ImageFile.writeImageFile(edgeRaster, new File(rootDir, "tmpedge.bmp").getAbsolutePath());

            final Transform transform = new Transform(sm, quantFactor, 1.0, 10.0);

            final Transform.HoughSpace houghSpace = transform.transform(edgeRaster, gradientDirRaster, houghThreshold);

            ImageFile.writeImageFile(c.add(houghSpace.createTransformCvMat()), new File(rootDir, "tmpht.bmp").getAbsolutePath());

            ImageFile.writeImageFile(c.addMat(transform.mask.getMaskImage()), new File(rootDir, "tmpmask.bmp").getAbsolutePath());
            ImageFile.writeImageFile(c.add(transform.gradDirMask.getMaskRaster()), new File(rootDir, "tmpdirmask.bmp").getAbsolutePath());

            final List<HoughSpaceEntry> hse = houghSpace.inverseTransform(sprocketInfoTiledImage, COVERLAY, ROVERLAY);

            final java.util.List<Transform.Cluster> clusters = transform.cluster(hse, clusterFactor);
            Transform.drawClusters(clusters, sprocketInfoTiledImage, BOVERLAY);

            final List<Transform.Fit> fits = transform.bestFit(clusters, sprocketInfoTiledImage, ROVERLAY, GOVERLAY);

            Transform.drawFits(fits, sprocketInfoTiledImage, YOVERLAY);

            // TODO: make this work - currently fails to write with twelvemonkeys
            // ImageFile.writeImageFile(Utils.mat2Img(sprocketInfoTiledImage, getOverlayCM()), new File(rootDir, "tmpbi.bmp").getAbsolutePath());

            assertEquals(16, fits.size());
        }
    }
}
