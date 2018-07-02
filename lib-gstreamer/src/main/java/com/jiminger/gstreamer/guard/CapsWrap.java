package com.jiminger.gstreamer.guard;

import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.Structure;

public class CapsWrap extends GstWrap<Caps> {

   public CapsWrap(final Caps caps) {
      super(caps);
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
