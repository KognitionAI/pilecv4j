package ai.kognition.pilecv4j.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.NativeLibrary;

// TODO: move the nr code to a separate library and delete this, moving the code back to the appropriate
// classes.
//
// TODO: Also remove the opencv dependency now in lib-nr pom file AND the need to process the resources AND
// the lib-nr resources since they only contain the property file for the opencv version substitution
public class HackLoadLibraryX {
   private static final Logger LOGGER = LoggerFactory.getLogger(HackLoadLibraryX.class);

   public static final String OCV_VERSION_PROPS = "opencv-info.version";
   public static final String OCV_SHORT_VERSION_PROP_NAME = "opencv-short.version";
   public static final String LIBNAME = "ai.kognition.pilecv4j";

   public static void init() {}

   static {
      // read a properties file from the classpath.
      final Properties ocvVersionProps = new Properties();
      try (InputStream ocvVerIs = HackLoadLibraryX.class.getClassLoader().getResourceAsStream(OCV_VERSION_PROPS)) {
         ocvVersionProps.load(ocvVerIs);
      } catch(final IOException e) {
         throw new IllegalStateException("Problem loading the properties file \"" + OCV_VERSION_PROPS + "\" from the classpath", e);
      }

      final String ocvShortVersion = ocvVersionProps.getProperty(OCV_SHORT_VERSION_PROP_NAME);
      if(ocvShortVersion == null)
         throw new IllegalStateException("Problem reading the short version from the properties file \"" + OCV_VERSION_PROPS + "\" from the classpath");

      LOGGER.debug("Loading the library for opencv with a short version {}", ocvShortVersion);

      NativeLibraryLoader.loader()
            .optional("opencv_ffmpeg" + ocvShortVersion + "_64")
            .library("opencv_java" + ocvShortVersion)
            .library(LIBNAME)
            .addCallback((dir, libname, oslibname) -> {
               if(LIBNAME.equals(libname))
                  NativeLibrary.addSearchPath(libname, dir.getAbsolutePath());
            })
            .load();
   }
}
