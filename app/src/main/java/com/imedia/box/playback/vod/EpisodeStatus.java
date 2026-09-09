package com.imedia.box.playback.vod;

import java.util.regex.Pattern;

public final class EpisodeStatus {

    private static final Pattern NUMBERED_COMPLETE = Pattern.compile(".*(?:\\u7b2c)?\\d+\\u96c6\\u5168.*");
    private static final Pattern TOTAL_COMPLETE = Pattern.compile(".*\\u5168\\d+\\u96c6.*");

    private EpisodeStatus() {
    }

    public static boolean isComplete(String remarks) {
        if (remarks == null) return false;
        String text = remarks.replaceAll("\\s+", "");
        return text.contains("\u5b8c\u7ed3")
                || text.contains("\u5b8c\u7d50")
                || text.contains("\u5168\u96c6")
                || text.contains("\u5927\u7ed3\u5c40")
                || text.contains("\u5927\u7d50\u5c40")
                || NUMBERED_COMPLETE.matcher(text).matches()
                || TOTAL_COMPLETE.matcher(text).matches();
    }
}
