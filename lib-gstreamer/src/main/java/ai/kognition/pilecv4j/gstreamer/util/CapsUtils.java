package ai.kognition.pilecv4j.gstreamer.util;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Structure;

public class CapsUtils {

    public static List<String> getMediaType(final Caps obj) {
        if(obj == null)
            return Collections.emptyList();

        final int numCaps = obj.size();
        return IntStream.range(0, numCaps)
            .mapToObj(i -> obj.getStructure(i))
            .filter(o -> o != null)
            .map(o -> o.getName())
            .filter(n -> n != null)
            .collect(Collectors.toList());
    }

    public static String get(final Caps obj, final String name) {
        if(obj == null)
            return null;

        final int numCaps = obj.size();
        for(int i = 0; i < numCaps; i++) {
            final Structure s = obj.getStructure(i);
            if(s != null) {
                final String ret = s.getString(name);
                if(ret != null) {
                    return ret;
                }
            }
        }
        return null;
    }
}
