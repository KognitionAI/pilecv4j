package com.jiminger.s8;
/***********************************************************************
    Legacy Film to DVD Project
    Copyright (C) 2005 James F. Carroll

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
****************************************************************************/

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.opencv.imgcodecs.Imgcodecs;

import com.jiminger.image.CvRaster;
import com.jiminger.image.mjpeg.MJPEGWriter;
import com.jiminger.nr.MinimizerException;
import com.jiminger.util.CommandLineParser;
import com.jiminger.util.FilenameUtils;
import com.jiminger.util.PropertiesUtils;

public class CorrelateFrames {
    public static final long megaBytes = 1024L * 1024L;
    public static final long defaultTileCacheSize = 300;
    public static long tileCacheSize = defaultTileCacheSize * megaBytes;
    public static final String[] colorChanelName = { "red", "green", "blue" };
    public static double minCoefConsideredMatch = 0.0;
    public static String outputDir = null;
    public static int maxoverlap = 5;
    public static double[] finalX = null;
    public static boolean listResults = false;
    public static String avifile = null;
    public static int avifps = 16;
    public static PrintStream workingPS = null;
    public static Properties overloadedMatches = null;

    private static java.util.List<FrameSet> frameSets = null;

    /** The main method. */
    public static void main(final String[] args)
            throws IOException, InterruptedException, Correlate.CorrelateException,
            MinimizerException {
        // First parse the command line and test for various
        // settings.
        if (!commandLine(args))
            System.exit(-1);

        final Iterator<FrameSet> iter = frameSets.iterator();
        FrameSet fs1 = iter.next();

        while (iter.hasNext()) {
            // now we need to consider, not just a single frame to frame match,
            // but diagonal to diagonal. An example of a diagonal set for 0->4 against
            // 0->4 would be:
            // 4->0
            // 4->1, 3->0
            // 4->2, 3->1, 2->0
            // 4->3, 3->2, 2->1, 1->0
            // 4->4, 3->3, 2->2, 1->1, 0->0
            // 3->4, 2->3, 1->2, 0->1
            // 2->4, 1->3, 0->2
            // 1->4, 0->3
            // 0->4
            // ... now the bottom 1/2 of this pyramid is really impossible.
            // as a matter of fact the last frame on the first strip must be
            // in the next strip somewhere so that's all we need to find ...

            final FrameSet fs2 = iter.next();

            // test to see if there is a match already

            if (!overloadedMatch(fs1, fs2, overloadedMatches)) {
                if (fs1.hasForcedMatchNext()) {
                    System.out.println("Overloaded match from frame " + fs1.getForcedMatchNextThisFrameNum() +
                            " in directory " + fs1.directoryName + " with frame " +
                            fs1.getForcedMatchNextNextFrameNum() + " in directory " +
                            fs2.directoryName);

                    writeWorkingFile(
                            new FrameMatch(fs1, fs1.getForcedMatchNextThisFrameNum(),
                                    fs2, fs1.getForcedMatchNextNextFrameNum(), 1.0),
                            true);

                    System.out.print("o");
                } else if (fs2.hasForcedMatchPrev()) {
                    System.out.println("Overloaded match from frame " + fs2.getForcedMatchPrevPrevFrameNum() +
                            " in directory " + fs1.directoryName + " with frame " +
                            fs2.getForcedMatchPrevThisFrameNum() + " in directory " +
                            fs2.directoryName);

                    writeWorkingFile(
                            new FrameMatch(fs1, fs2.getForcedMatchPrevPrevFrameNum(),
                                    fs2, fs2.getForcedMatchPrevThisFrameNum(), 1.0),
                            true);

                    System.out.print("o");
                } else {

                    final CvRaster frameImage1 = loadLastImage(fs1);

                    if (frameImage1 == null) {
                        System.out.print("N");
                        writeErrorToWorkingFile(fs1, fs2, true);
                    } else {
                        final int maxi = fs1.lastValid();
                        final CvRaster[] frameImage2 = loadImages(fs2);

                        // we are only considering a single frame from the first
                        // frameset
                        final double[][] r = new double[fs2.numFrames][];

                        // -------------------------------------------------------
                        // Carry out the actual correlation between the combinations
                        // of every frame to every other frame in successive strips.
                        // -------------------------------------------------------
                        // we used to do the above. Now we just look for the
                        // last image of the first strip in the second strip
                        // without going over the max overlap
                        int checked = 0;
                        for (int j = 0; j < fs2.numFrames && checked < maxoverlap; j++) {
                            if (frameImage2[j] != null) {
                                r[j] = Correlate.correlation(frameImage1, frameImage2[j]);
                                checked++;
                            } else
                                r[j] = null;
                        }
                        // -------------------------------------------------------

                        // checked will be zero if there are no frames in frameset 2
                        if (checked > 0) {
                            // -------------------------------------------------------
                            // now i need to interrogate the results and find the peak
                            // in the correlation. Find the highest single
                            // value in the correlation and find the highest in
                            // each first strip frame vs all second strips (or
                            // each row of the correlation matrix 'r').
                            // ------------------------------------------------------
                            // int peakIndex;
                            double peakIndexValue;

                            double maxCoef = -2.0;
                            int maxj = -1;
                            // int maxb = -1;
                            // peakIndex = -1;
                            peakIndexValue = -2.0;
                            checked = 0;
                            for (int j = 0; j < fs2.numFrames && checked < maxoverlap; j++) {
                                if (r[j] != null) {
                                    for (int b = 0; b < 3; b++) {
                                        if (maxCoef < r[j][b]) {
                                            maxCoef = r[j][b];
                                            maxj = j;
                                            // maxb = b;
                                        }

                                        if (peakIndexValue < r[j][b]) {
                                            peakIndexValue = r[j][b];
                                            // peakIndex = j;
                                        }
                                    }

                                    checked++;
                                }
                            }

                            if (maxCoef > minCoefConsideredMatch) {
                                writeWorkingFile(
                                        new FrameMatch(fs1, maxi, fs2, maxj, maxCoef));
                                System.out.print(".");
                            } else {
                                writeErrorToWorkingFile(fs1, maxi, fs2, maxj, maxCoef, minCoefConsideredMatch);
                                System.out.print("X");
                            }
                        } // end - if there are any frames from frameset 2
                    } // end - if there is an image for fs1
                } // end - do the correlation if there is no match
            } // end - if match is not overloaded
            else
                System.out.print("o"); // otherwise it was overloaded

            fs1 = fs2; // prepare for the next iteration
        } // end loop over all strips

        if (workingPS != null) {
            workingPS.flush();
            workingPS.close();
        }
        System.out.println();

        if (listResults)
            for (final FrameSet tmpfs : frameSets)
                tmpfs.outputStripInformation();

        if (outputDir != null) {
            final File dir = new File(outputDir);
            dir.mkdir();

            int framenum = 0;
            for (final FrameSet fs : frameSets)
                framenum = fs.moveStrip(framenum, outputDir, "f", 5, finalX);
        }

        if (avifile != null) {
            try {
                MJPEGWriter.initializeMJPEG(avifile);
                boolean working = true;

                for (final Iterator<FrameSet> iter2 = frameSets.iterator(); iter2.hasNext() && working;) {
                    final FrameSet fs = iter2.next();
                    final java.util.List<String> filenames = fs.getStripFilenames();
                    for (int i = 0; i < filenames.size() && working; i++) {
                        final String filename = filenames.get(i);
                        working = MJPEGWriter.appendFile(filename);
                        if (!working)
                            System.err.println("Failed to add file " + filename + " to mjpeg " + avifile);
                    }
                }

                if (working)
                    working = MJPEGWriter.close(avifps);

                if (!working)
                    System.err.println("Failed!");
            } finally {
                MJPEGWriter.cleanUp();
            }
        }
    }

    static private void writeWorkingFile(final FrameMatch match) {
        writeWorkingFile(match, false);
    }

    static private void writeWorkingFile(final FrameMatch match, final boolean overloaded) {
        if (workingPS != null) {
            workingPS.println(match.prev.getBaseName() + ".next.match=" + match.prevFrameNumber + "," + match.nextFrameNumber);
            workingPS.println(match.prev.getBaseName() + ".next.coef=" + match.coef);
            if (overloaded)
                workingPS.println(match.prev.getBaseName() + ".next.overloaded=true");
            workingPS.println();
            workingPS.flush();
        }
    }

    static private void writeErrorToWorkingFile(final FrameSet fs1, final FrameSet fs2, final boolean firstFrameNull) {
        if (workingPS != null) {
            workingPS.println(
                    "# No frames for " + (firstFrameNull ? fs1.getBaseName() : fs2.getBaseName()) +
                            " while correllating " + fs1.getBaseName() + " and " + fs2.getBaseName());
            workingPS.flush();
        }
    }

    static private void writeErrorToWorkingFile(final FrameSet fs1, final int maxi, final FrameSet fs2, final int maxj, final double maxCoef,
            final double minRequired) {
        if (workingPS != null) {
            workingPS.println("# too low coef " + fs1.getBaseName() + " to " +
                    fs2.getBaseName() + ". coef is " + maxCoef +
                    " with " + minRequired + " required");
            workingPS.println(fs1.getBaseName() + ".next.match=" + maxi + "," + maxj);
            workingPS.println(fs1.getBaseName() + ".next.coef=" + maxCoef);
            workingPS.println(fs1.getBaseName() + ".next.nomatch=true");
            workingPS.println();
            workingPS.flush();
        }
    }

    static private boolean overloadedMatch(final FrameSet fs1, final FrameSet fs2, final Properties matches) {
        if (matches == null)
            return false;

        final String fs1base = fs1.getBaseName();
        Properties tmpp = PropertiesUtils.getSection(matches, fs1base, false);
        boolean baseIsFS1 = true;
        final String fs2base = fs2.getBaseName();

        if (tmpp == null) {
            tmpp = PropertiesUtils.getSection(matches, fs2base, false);
            if (tmpp == null)
                return false;
            baseIsFS1 = false;
        }

        // we have a possible section
        Properties matchSection = null;
        if (baseIsFS1) {
            // see if there is a "next" section
            matchSection = PropertiesUtils.getSection(tmpp, "next", false);
            if (matchSection == null)
                matchSection = PropertiesUtils.getSection(tmpp, fs2base, false);
        } else {
            // see if there is a "prev" section
            matchSection = PropertiesUtils.getSection(tmpp, "prev", false);
            if (matchSection == null)
                matchSection = PropertiesUtils.getSection(tmpp, fs1base, false);
        }

        if (matchSection == null)
            return false;

        final String matchStr = matchSection.getProperty("match");
        if (matchStr == null) {
            System.out.println();
            System.out.println("Match file seems to be missing a \"match\" entry for framesets \"" +
                    fs1base + "\" and \"" + fs2base + "\"");
            System.out.println();
            return false;
        }

        if ("none".equalsIgnoreCase(matchStr))
            return true; // exit but say everything is ok.

        final StringTokenizer stok = new StringTokenizer(matchStr, ",");
        if (stok.countTokens() != 2) {
            System.out.println();
            System.out.println("Match file seems to have an invalid \"match\" entry for framesets \"" +
                    fs1base + "\" and \"" + fs2base + "\"");
            System.out.println();
            return false;
        }

        int fs1matchframe = Integer.parseInt(stok.nextToken());
        int fs2matchframe = Integer.parseInt(stok.nextToken());

        if (fs1matchframe < fs2matchframe) {
            final int tmpi = fs1matchframe;
            fs2matchframe = fs1matchframe;
            fs1matchframe = tmpi;
        }

        double coef = 1.0;
        final String tmps = matchSection.getProperty("coef");
        if (tmps != null)
            coef = Double.parseDouble(tmps);

        writeWorkingFile(
                new FrameMatch(fs1, fs1matchframe, fs2, fs2matchframe, coef), true);
        return true;
    }

    static private CvRaster[] loadImages(final FrameSet fs) {
        /*
         * Create an input stream from the specified file name to be used with the file decoding operator.
         */
        final String[] filenames = new String[fs.numFrames];
        for (int i = 0; i < fs.numFrames; i++) {
            if (!fs.isDropped(i) && !fs.isOutOfBounds(i) && fs.isCut(i)) {
                filenames[i] = fs.getFileName(i);
            } else
                filenames[i] = null;
        }

        /* Create an operator to decode the image file. */
        final CvRaster[] frameImage = new CvRaster[fs.numFrames];
        for (int i = 0; i < fs.numFrames; i++) {
            if (filenames[i] != null)
                frameImage[i] = CvRaster.manageCopy(Imgcodecs.imread(filenames[i]));
            else
                frameImage[i] = null;
        }

        return frameImage;
    }

    static private CvRaster loadLastImage(final FrameSet fs) {
        /*
         * Create an input stream from the specified file name to be used with the file decoding operator.
         */
        String filename = null;
        for (int i = fs.numFrames - 1; i >= 0 && filename == null; i--) {
            if (!fs.isDropped(i) && !fs.isOutOfBounds(i) && fs.isCut(i)) {
                filename = fs.getFileName(i);
            }
        }

        return CvRaster.manageCopy(Imgcodecs.imread(filename));
    }

    static private boolean commandLine(final String[] args) {
        final CommandLineParser cl = new CommandLineParser(args);

        // see if we are asking for help
        if (cl.getProperty("help") != null ||
                cl.getProperty("-help") != null) {
            usage();
            return false;
        }

        String dirlist = null;
        String parentDir = null;

        String tmps = cl.getProperty("f");
        if (tmps != null)
            dirlist = tmps;

        tmps = cl.getProperty("pdir");
        if (tmps != null)
            parentDir = tmps;

        final ArrayList<String> dirs = new ArrayList<String>();
        if (dirlist != null) {
            final StringTokenizer stok = new StringTokenizer(dirlist, ",");
            if (stok.countTokens() < 2) {
                System.out.println("you must supply at least two valid directories for the -f option.");
                usage();
                return false;
            }
            while (stok.hasMoreTokens())
                dirs.add(stok.nextToken());
        } else if (parentDir != null) {
            final File pdir = new File(parentDir);

            if (!pdir.isDirectory()) {
                System.out.println("\"" + parentDir + "\" is not a directory.");
                usage();
                return false;
            }

            final File[] subdirs = pdir.listFiles(
                    new FileFilter() {
                        @Override
                        public boolean accept(final File f) {
                            return f.isDirectory();
                        }
                    });

            for (int i = 0; i < subdirs.length; i++)
                dirs.add(subdirs[i].getAbsolutePath());

            Collections.sort(dirs);
        } else {
            usage();
            return false;
        }

        frameSets = loadAllFramsets(dirs);

        if (frameSets == null) {
            usage();
            return false;
        }

        outputDir = cl.getProperty("o");
        if (outputDir != null) {
            tmps = cl.getProperty("x");
            if (tmps != null) {
                final StringTokenizer stok = new StringTokenizer(tmps, ",");
                if (stok.countTokens() != 3) {
                    usage();
                    return false;
                }

                finalX = new double[3];
                for (int i = 0; stok.hasMoreTokens(); i++)
                    finalX[i] = Double.parseDouble(stok.nextToken());

                finalX[2] = Math.log((1.0 / finalX[2]));
            }
        }

        tmps = cl.getProperty("mo");
        if (tmps != null)
            maxoverlap = Integer.parseInt(tmps);

        tmps = cl.getProperty("cs");
        if (tmps != null)
            tileCacheSize = Long.parseLong(tmps) * megaBytes;

        tmps = cl.getProperty("lr");
        if ("true".equalsIgnoreCase(tmps) || "t".equalsIgnoreCase(tmps) ||
                "yes".equalsIgnoreCase(tmps) || "y".equalsIgnoreCase(tmps) ||
                "1".equalsIgnoreCase(tmps))
            listResults = true;

        avifile = cl.getProperty("avi");
        if (avifile != null) {
            final File tfile = new File(avifile);
            avifile = tfile.getAbsolutePath();
        }

        tmps = cl.getProperty("avifps");
        if (tmps != null)
            avifps = Integer.parseInt(tmps);

        tmps = cl.getProperty("mc");
        if (tmps != null)
            minCoefConsideredMatch = Double.parseDouble(tmps);

        tmps = cl.getProperty("match");
        if (tmps != null) {
            overloadedMatches = new Properties();
            if (!PropertiesUtils.loadProps(overloadedMatches, tmps)) {
                System.err.println("Couldn't load \"" + tmps + "\"");
                System.exit(-1);
            }
        }

        tmps = cl.getProperty("wf");
        if (tmps != null) {
            try {
                final FileOutputStream fos = new FileOutputStream(tmps);
                workingPS = new PrintStream(fos);
            } catch (final IOException ioe) {
                System.out.println("Failed to open working file.");
                return false;
            }
        }

        return true;
    }

    private static java.util.List<FrameSet> loadAllFramsets(final java.util.List<String> dirs) {
        if (dirs.size() < 2) {
            System.out.println("you must supply at least two valid directories.");
            return null;
        }

        final java.util.List<FrameSet> ret = new ArrayList<FrameSet>();

        for (int i = 0; i < dirs.size(); i++) {
            final FrameSet fs = new FrameSet(dirs.get(i));
            ret.add(fs);
            if (!loadFrameSet(fs)) {
                System.out.println("invalid frame set directory at \"" + fs.directoryName + "\"");
                return null;
            }
        }

        return ret;
    }

    private static boolean loadFrameSet(final FrameSet frameset) {
        final String directoryName = frameset.directoryName;
        final String propFileName = directoryName + File.separator + "frames.properties";
        final String matchFileName = directoryName + File.separator + "match.properties";

        final Properties p = new Properties();
        if (!PropertiesUtils.loadProps(p, propFileName)) {
            System.err.println("Could not load file \"" + propFileName +
                    "\". Perhaps \"" + directoryName +
                    "\" is not a valid frame directory.");
            return false;
        }

        frameset.prop = p;

        String tmps = p.getProperty("frames.numberofframes");
        if (tmps == null) {
            System.err.println("Properties file \"" + propFileName
                    + "\" does not appear to contain valid frame information. It is missing the entry for \"frames.numberofframes\"");
            return false;
        }

        frameset.numFrames = Integer.parseInt(tmps);

        frameset.frameFileNames = new String[frameset.numFrames];
        for (int i = 0; i < frameset.numFrames; i++) {
            final String frameFilenameEntry = "frames." + Integer.toString(i) + ".filename";
            frameset.frameFileNames[i] = p.getProperty(frameFilenameEntry);
            if (frameset.frameFileNames[i] == null) {
                final boolean dropped = ("true".equalsIgnoreCase(p.getProperty("frames." + Integer.toString(i) + ".dropped")));
                final boolean cutFrame = ("true".equalsIgnoreCase(p.getProperty("frames." + Integer.toString(i) + ".cutFrame")));

                if (!dropped && cutFrame) {
                    System.err.println("Properties file \"" + propFileName
                            + "\" does not appear to contain valid frame information. It is missing the entry for \"" + frameFilenameEntry + "\"");
                    return false;
                }
            }
        }

        // test for a match prop file
        final File matchFile = new File(matchFileName);
        if (matchFile.exists()) {
            final Properties matchProp = new Properties();

            if (!PropertiesUtils.loadProps(matchProp, matchFileName)) {
                System.err.println("Could not load file \"" + matchFileName +
                        "\", though it seems to exist.");
                return false;
            }

            for (final Map.Entry<Object, Object> entry : matchProp.entrySet()) {
                final String key = (String) entry.getKey();

                if (!key.startsWith("match")) {
                    System.err.println("Match prop file \"" + matchFileName + "\" seems to have the wrong format.");
                    return false;
                }

                final int dotindex = key.indexOf(".");
                if (dotindex < 0) {
                    System.err.println("Match prop file \"" + matchFileName + "\" seems to have the wrong format.");
                    return false;
                }

                final int thisFrameNum = Integer.parseInt(key.substring(dotindex + 1));

                tmps = (String) entry.getValue();

                final StringTokenizer stok = new StringTokenizer(tmps, ",");
                if (stok.countTokens() != 2) {
                    System.err.println("Match prop file \"" + matchFileName + "\" seems to have the wrong format.");
                    return false;
                }

                final String prevnextstr = stok.nextToken();
                if ("prev".equalsIgnoreCase(prevnextstr))
                    frameset.setForcedMatchPrev(thisFrameNum, Integer.parseInt(stok.nextToken()));
                else if ("next".equalsIgnoreCase(prevnextstr))
                    frameset.setForcedMatchNext(thisFrameNum, Integer.parseInt(stok.nextToken()));
                else {
                    System.err.println("Match prop file \"" + matchFileName + "\" seems to have the wrong format.");
                    return false;
                }

            }
        }

        return true;
    }

    public static class FrameSet {
        public String[] frameFileNames;
        public int numFrames;
        public Properties prop;
        public String directoryName;
        public FrameMatch prevMatch = null;
        public FrameMatch nextMatch = null;

        private int[] forcedMatchPrev = null;
        private int[] forcedMatchNext = null;

        public FrameSet(final String directoryName) {
            final File tfile = new File(directoryName);
            this.directoryName = tfile.getAbsolutePath();
        }

        public String getBaseName() {
            final int index = directoryName.lastIndexOf(File.separatorChar);
            return index < 0 ? directoryName : directoryName.substring(index + 1);
        }

        public String getFileName(final int frameIndex) {
            return directoryName + File.separator + frameFileNames[frameIndex];
        }

        public String getFileExtention(final int frameIndex) {
            final String fname = getFileName(frameIndex);
            final int dotindex = fname.lastIndexOf('.');
            return fname.substring(dotindex + 1, fname.length());
        }

        public String lookup(final String property, final int frameIndex) {
            return prop.getProperty("frames." + Integer.toString(frameIndex) + "." + property);
        }

        public boolean isOutOfBounds(final int framenum) {
            return "true".equalsIgnoreCase(lookup("outofbounds", framenum));
        }

        public boolean isCut(final int framenum) {
            return "true".equalsIgnoreCase(lookup("cutFrame", framenum));
        }

        public boolean isDropped(final int framenum) {
            return "true".equalsIgnoreCase(lookup("dropped", framenum));
        }

        public int lastValid() {
            int ret = -1;
            for (int i = numFrames - 1; i >= 0 && ret == -1; i--)
                if (!isDropped(i) && !isOutOfBounds(i) && isCut(i))
                    ret = i;

            return ret;
        }

        public java.util.List<Integer> getStripIndicies() {
            final java.util.List<Integer> ret = new ArrayList<Integer>();

            int startframe = 0;
            if (prevMatch != null)
                startframe = prevMatch.nextFrameNumber;
            else if (isOutOfBounds(startframe))
                startframe++;

            int endframe = numFrames - 1;
            if (nextMatch != null)
                endframe = nextMatch.prevFrameNumber - 1;
            else if (isOutOfBounds(endframe))
                endframe--;

            for (int i = startframe; i <= endframe; i++) {
                if (isCut(i) && !isDropped(i) && !isOutOfBounds(i))
                    ret.add(new Integer(i));
            }

            return ret;
        }

        public java.util.List<String> getStripFilenames() {
            final java.util.List<String> ret = new ArrayList<String>();
            final java.util.List<Integer> si = getStripIndicies();
            for (int i = 0; i < si.size(); i++)
                ret.add(getFileName(si.get(i).intValue()));
            return ret;
        }

        public void outputStripInformation() {
            final java.util.List<String> si = getStripFilenames();
            for (int i = 0; i < si.size(); i++)
                System.out.println(si.get(i));
        }

        public int moveStrip(int startingFramenum, final String destDir, final String prefix, final int integerLength, final double[] x)
                throws IOException, MinimizerException {
            final List<Integer> si = getStripIndicies();

            for (int ii = 0; ii < si.size(); ii++) {
                final int filenum = si.get(ii).intValue();
                final String outfilename = destDir + File.separator + prefix +
                        FilenameUtils.lengthConstToString(startingFramenum, integerLength) +
                        "." + /* getFileExtention(filenum) */ "jpeg";
                startingFramenum++;

                final FileInputStream fis = new FileInputStream(getFileName(filenum));
                final BufferedInputStream bis = new BufferedInputStream(fis, 8096);
                final FileOutputStream fos = new FileOutputStream(outfilename);
                final BufferedOutputStream bos = new BufferedOutputStream(fos, 8096);

                int c;
                try {
                    while ((c = bis.read()) != -1)
                        bos.write(c);
                } finally {
                    try {
                        bos.flush();
                    } catch (final Throwable th) {}
                    try {
                        fos.flush();
                    } catch (final Throwable th) {}
                    try {
                        bos.close();
                    } catch (final Throwable th) {}
                    try {
                        fos.close();
                    } catch (final Throwable th) {}
                    try {
                        bis.close();
                    } catch (final Throwable th) {}
                    try {
                        fis.close();
                    } catch (final Throwable th) {}
                }
            }

            return startingFramenum;
        }

        public void setForcedMatchPrev(final int thisFrameNum, final int other) {
            forcedMatchPrev = new int[2];
            forcedMatchPrev[0] = thisFrameNum;
            forcedMatchPrev[1] = other;
        }

        public void setForcedMatchNext(final int thisFrameNum, final int other) {
            forcedMatchNext = new int[2];
            forcedMatchNext[0] = thisFrameNum;
            forcedMatchNext[1] = other;
        }

        public boolean hasForcedMatchPrev() {
            return forcedMatchPrev == null ? false : true;
        }

        public boolean hasForcedMatchNext() {
            return forcedMatchNext == null ? false : true;
        }

        public int getForcedMatchPrevThisFrameNum() {
            return forcedMatchPrev[0];
        }

        public int getForcedMatchPrevPrevFrameNum() {
            return forcedMatchPrev[1];
        }

        public int getForcedMatchNextThisFrameNum() {
            return forcedMatchNext[0];
        }

        public int getForcedMatchNextNextFrameNum() {
            return forcedMatchNext[1];
        }
    }

    public static class FrameMatch {
        public FrameMatch(final FrameSet prev, final int prevframe, final FrameSet next, final int nextframe, final double coef) {
            this.prev = prev;
            this.next = next;
            this.prevFrameNumber = prevframe;
            this.nextFrameNumber = nextframe;
            this.coef = coef;
            this.prev.nextMatch = this;
            this.next.prevMatch = this;
        }

        public double coef;
        public int prevFrameNumber;
        public int nextFrameNumber;
        public FrameSet prev;
        public FrameSet next;
    }

    private static void usage() {
        System.out.println("usage: java [javaargs] CorrelateFrames -f pathToDirectoryOfFirstFrameset,pathToDirectoryOfSecFrameset[,...] [options]");
        System.out.println("   or: java [javaargs] CorrelateFrames -pdir parentDirectory [options]");
        System.out.println(
                "       where options can be: [-o outputdirectory [-x 1,255,1.0]|[-contrast]] [-mo 5] [-mc 0.0] [-match matchfile] [-lr] [-avi avifilename [-avifps 16]] [-wf workingFileName]");
        System.out.println();
        System.out.println("  when -pdir is supplied all subdirectories (files are ignored) are added to a list and");
        System.out.println("    sorted lexographically (i.e. in alphabetical order for anyone but those that wrote");
        System.out.println("    the javadocs from java.lang.String).");
        System.out.println();
        System.out.println("     -o: output directory where the frames will be sent.");
        System.out.println("     -x: if -o is specified, provides the right, left and middle value in the Epson scanner histogram screen.");
        System.out.println("     -contrast: if -o is specified and not -x, identifes the output images to be histogram equalized.");
        System.out.println("     -mo: maximum number of frames to consider for overlap.");
        System.out.println("     -mc: min correlation coef value to be considered a match. TAKE NOTE:");
        System.out.println("          this is currently set to 0 by default. In other words, it just takes");
        System.out.println("          the most likely candidate regardless of the coef (unless they are");
        System.out.println("          actually inversley correllated).");
        System.out.println("     -match: provides a file with overloaded matches.");
        System.out.println("     -lr: list the ordered results.");
        System.out.println("     -avi: generate an mjpeg avi file from the results.");
        System.out.println("     -avifps: if -avi is specified, provides the frames per second use in the avifile.");
        System.out.println("     -wf: if -wf is specified, the named file will have all of the matches written to it as they are found.");
    }
}
