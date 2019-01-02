package ai.kognition.pilecv4j.image.tiff;

//import java.io.PrintStream;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.stream.IntStream;
//
//import ai.kognition.pilecv4j.image.CvMat;
//
//import mil.nga.tiff.FieldTagType;
//import mil.nga.tiff.FileDirectory;
//import mil.nga.tiff.FileDirectoryEntry;
//import mil.nga.tiff.Rasters;
//import mil.nga.tiff.TIFFImage;
//
//public class TiffUtils {
//   public static void dumpMeta(final TIFFImage image, final PrintStream ps) {
//      dumpMeta(image, ps, false);
//   }
//
//   public static void dumpMeta(final TIFFImage image, final PrintStream ps, final boolean readRasters) {
//      ps.println("TIFFImage [");
//      ps.println("  fileDirectories=[");
//      image.getFileDirectories().stream().forEach(d -> dumpMeta(d, ps, 4, readRasters));
//      ps.println("  ]");
//      ps.println("]");
//   }
//
//   public static Set<FieldTagType> toAbbreviate = new HashSet<>();
//   static {
//      toAbbreviate.add(FieldTagType.XMP);
//      toAbbreviate.add(FieldTagType.StripOffsets);
//      toAbbreviate.add(FieldTagType.StripByteCounts);
//      toAbbreviate.add(FieldTagType.ICCProfile);
//   }
//
//   public static void dumpMeta(final FileDirectory ifd, final PrintStream ps) {
//      dumpMeta(ifd, ps, 0, false);
//   }
//
//   public static void dumpMeta(final FileDirectory ifd, final PrintStream ps, final int indent) {
//      dumpMeta(ifd, ps, indent, false);
//   }
//
//   public static void dumpMeta(final FileDirectory ifd, final PrintStream ps, final int indent, final boolean readRasters) {
//      final StringBuilder sb = new StringBuilder();
//      IntStream.range(0, indent).forEach(i -> sb.append(' '));
//      final String indentStr = sb.toString();
//
//      ps.println(indentStr + "FileDirectory [");
//      ps.println(indentStr + "  "
//            + "reader=" + ifd.getReader() + ", tiled=" + ifd.isTiled()
//            + ", planarConfiguration=" + ifd.getPlanarConfiguration() + ", decoder=" + ifd.getDecoder().getClass().getSimpleName() + ", cache="
//            + ", writeRasters=" + ifd.getWriteRasters()
//
//      );
//
//      ifd.getEntries().stream().forEach(e -> dumpMeta(e, ps, indent + 2));
//
//      ps.println(indentStr + "  fieldTagTypeMapping=[");
//      ifd.getFieldTagTypeMapping().forEach((k, v) -> {
//         ps.print(indentStr + "    " + k + "=");
//         dumpMeta(v, ps, 0);
//      });
//      ps.println(indentStr + "  ]");
//
//      if(readRasters) {
//         ps.print(indentStr + "  ");
//         dumpMeta(ifd.readRasters(), ps);
//      }
//
//      ps.println(indentStr + "]");
//   }
//
//   public static CvMat createCvMatFromTiffRasters(final Rasters r) {
//      return null;
//   }
//
//   public static void dumpMeta(final FileDirectoryEntry entry, final PrintStream ps, final int indent) {
//      final StringBuilder sb = new StringBuilder();
//      IntStream.range(0, indent).forEach(i -> sb.append(' '));
//      final String indentStr = sb.toString();
//      ps.print(
//            indentStr + "FileDirectoryEntry [fieldTag=" + entry.getFieldTag() + ", fieldType=" + entry.getFieldType() + ", typeCount=" + entry.getTypeCount());
//
//      if(toAbbreviate.contains(entry.getFieldTag()))
//         ps.println(", values=[...]");
//      else
//         ps.println(", values=" + entry.getValues() + "]");
//   }
//
//   public static void dumpMeta(final Rasters r, final PrintStream ps) {
//      ps.println("Raster [" +
//            "rasterType=" + (r.hasSampleValues() ? "SampleValues" : (r.hasInterleaveValues() ? "Interleaved" : "unknown")) +
//            ", numpixels=" + r.getNumPixels() +
//            ", depth=" + r.getBitsPerSample() +
//            ", dim=" + r.getWidth() + "x" + r.getHeight() +
//            "]"
//
//      );
//   }
//
//}
