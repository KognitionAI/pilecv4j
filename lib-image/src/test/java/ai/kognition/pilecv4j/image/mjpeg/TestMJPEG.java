package ai.kognition.pilecv4j.image.mjpeg;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import ai.kognition.pilecv4j.image.mjpeg.MJPEGWriter;

public class TestMJPEG {

    final String animationDir = new File(
            getClass().getClassLoader().getResource("animation").getFile()).getAbsolutePath();

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Test
    public void testMJPEGWriter() throws Exception {
        final File aviout = tmpFolder.newFile();
        MJPEGWriter.main(new String[] { "-pdir", animationDir, "-avifile", aviout.getAbsolutePath() });
        assertTrue(aviout.exists());
    }

    @Test
    public void testMJPEGWriterDumbFailure() throws Exception {
        final File aviout = tmpFolder.newFile();
        assertFalse(MJPEGWriter.commandLine(new String[] { "-avifile", aviout.getAbsolutePath() }));
        assertFalse(MJPEGWriter.commandLine(new String[] { "-pdir", "/no/directory/here", "-avifile", aviout.getAbsolutePath() }));
    }

}
