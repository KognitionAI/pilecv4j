package ai.kognition.pilecv4j.gstreamer.guard;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Structure;

public class CapsWrap extends GstWrap<Caps> {

   public CapsWrap(final Caps caps) {
      super(caps);
   }

   public List<String> getMediaType() {
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

   public String get(final String name) {
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
