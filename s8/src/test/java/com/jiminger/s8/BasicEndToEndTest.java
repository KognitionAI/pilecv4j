package com.jiminger.s8;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class BasicEndToEndTest {

    String testFile = new File(BasicEndToEndTest.class.getClassLoader().getResource("img00047.jp2").getFile()).getPath();

    @Test
    public void testExtractFrames() throws Exception {
        System.out.println(testFile);

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
