package com.imedia.box.player.track;

import android.content.Context;

import com.imedia.box.R;

import java.util.Locale;

public final class PlaybackLanguage {

    private static final String[] TAGS = {"", "zh-Hans", "zh-Hant", "en", "ja", "ko", "es", "fr", "de", "pt", "ar"};

    private PlaybackLanguage() {
    }

    public static int count() {
        return TAGS.length;
    }

    public static String tag(int index) {
        return TAGS[Math.clamp(index, 0, TAGS.length - 1)];
    }

    public static String[] labels(Context context) {
        String[] labels = new String[TAGS.length];
        labels[0] = context.getString(R.string.player_language_device);
        for (int i = 1; i < TAGS.length; i++) {
            labels[i] = Locale.forLanguageTag(TAGS[i]).getDisplayName(Locale.getDefault());
        }
        return labels;
    }
}
