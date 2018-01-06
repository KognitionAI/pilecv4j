package com.jiminger.s8;

import static org.junit.Assert.assertEquals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BasicEndToEndTest {

    @Rule
    public TemporaryFolder outputDir = new TemporaryFolder();

    String testFileName = "img00047.jp2";

    @Test
    public void testExtractFrames() throws Exception {
        // copy the testfile into the temporary folder.
        try (InputStream is = new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(testFileName));) {

            // copy the file into the temp folder
            final File rootDir = new File("/tmp"); // outputDir.newFolder();
            final String testFile = new File(rootDir, testFileName).getAbsolutePath();

            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(testFile))) {
                IOUtils.copyLarge(is, os);
            }

            final int index = testFile.lastIndexOf(".");
            final String outDir = testFile.substring(0, index);
            final File outDirFile = new File(outDir);
            if (outDirFile.exists())
                FileUtils.deleteDirectory(outDirFile);

            final String propertyFileName = outDir + File.separator + "frames.properties";

            ExtractFrames.main(new String[] { "-f", testFile, "-rev", "-rl", "-di" });

            final Properties props = new Properties();
            props.load(new FileInputStream(new File(propertyFileName)));

            assertEquals("16", props.getProperty("frames.numberofframes"));
        }
    }

}
