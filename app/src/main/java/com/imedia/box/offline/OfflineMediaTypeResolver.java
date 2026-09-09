package com.imedia.box.offline;

import androidx.annotation.Nullable;
import androidx.media3.common.MimeTypes;

import java.util.Locale;

final class OfflineMediaTypeResolver {

    private OfflineMediaTypeResolver() {
    }

    @Nullable
    static String adaptiveMimeType(@Nullable String declaredFormat, @Nullable String responseContentType, @Nullable String preview) {
        String type = adaptiveType(declaredFormat);
        if (type != null) return type;
        type = adaptiveType(responseContentType);
        if (type != null) return type;
        if (preview == null) return null;
        String content = trimLeading(preview).toUpperCase(Locale.ROOT);
        if (content.startsWith("#EXTM3U")) return MimeTypes.APPLICATION_M3U8;
        if (content.contains("<MPD") && (content.startsWith("<?XML") || content.startsWith("<MPD"))) return MimeTypes.APPLICATION_MPD;
        return null;
    }

    private static String trimLeading(String value) {
        int index = 0;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character != '\ufeff' && !Character.isWhitespace(character)) break;
            index++;
        }
        return value.substring(index);
    }

    @Nullable
    private static String adaptiveType(@Nullable String value) {
        if (value == null) return null;
        String type = value.toLowerCase(Locale.ROOT);
        if (type.contains("m3u8") || type.contains("mpegurl") || type.contains("application/hls")) return MimeTypes.APPLICATION_M3U8;
        if (type.contains("mpd") || type.contains("dash+xml") || type.contains("application/dash")) return MimeTypes.APPLICATION_MPD;
        return null;
    }
}
