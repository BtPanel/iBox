package com.imedia.box.diagnostics;

import java.net.URI;
import java.util.Locale;

public final class SourceHealthAddress {

    private SourceHealthAddress() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null) return "";
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (scheme.equals("file") || scheme.equals("content") || scheme.equals("assets") || scheme.equals("asset")) {
                return scheme + "://local";
            }
            if (!scheme.equals("http") && !scheme.equals("https")) return "";
            if (uri.getHost() == null || uri.getHost().isBlank()) return "";
            return new URI(scheme, null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toASCIIString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
