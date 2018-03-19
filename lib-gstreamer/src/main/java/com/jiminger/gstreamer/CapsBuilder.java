package com.jiminger.gstreamer;

import java.nio.ByteOrder;

import org.freedesktop.gstreamer.Caps;

public class CapsBuilder {
    private StringBuilder sb = null;

    public CapsBuilder(final String init) {
        add(init);
    }

    public CapsBuilder add(final String entry) {
        check().append(entry);
        return this;
    }

    public CapsBuilder add(final String key, final String value) {
        check().append(key).append("=").append(value);
        return this;
    }

    public CapsBuilder addFormatConsideringEndian() {
        return (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) ? add("format=BGR") : add("format=RGB");
    }

    public Caps build() {
        return sb == null ? Caps.emptyCaps() : new Caps(sb.toString());
    }

    public String buildString() {
        return sb == null ? Caps.emptyCaps().toString() : sb.toString();
    }

    private StringBuilder check() {
        if (sb == null)
            sb = new StringBuilder();
        else
            sb.append(",");
        return sb;
    }
}
