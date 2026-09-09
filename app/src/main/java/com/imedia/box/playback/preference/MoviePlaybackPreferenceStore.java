package com.imedia.box.playback.preference;

import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;
import com.imedia.box.App;

import java.lang.reflect.Type;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MoviePlaybackPreferenceStore {

    static final int MAX_ENTRIES = 100;
    private static final String KEY = "movie_playback_preferences";
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, MoviePlaybackPreference>>() {}.getType();

    private MoviePlaybackPreferenceStore() {
    }

    public static MoviePlaybackPreference get(String key) {
        return isEmpty(key) ? null : read().get(key);
    }

    public static void put(String key, MoviePlaybackPreference preference) {
        if (isEmpty(key) || preference == null) return;
        Map<String, MoviePlaybackPreference> values = read();
        values.put(key, new MoviePlaybackPreference(preference.engine(), preference.decode(), preference.speed(),
                preference.subtitleSource(), preference.subtitleScale(), preference.subtitlePosition(), System.currentTimeMillis()));
        Prefers.put(KEY, App.gson().toJson(trim(values)));
    }

    public static void remove(String key) {
        if (isEmpty(key)) return;
        Map<String, MoviePlaybackPreference> values = read();
        if (values.remove(key) != null) Prefers.put(KEY, App.gson().toJson(values));
    }

    static LinkedHashMap<String, MoviePlaybackPreference> trim(Map<String, MoviePlaybackPreference> values) {
        LinkedHashMap<String, MoviePlaybackPreference> result = new LinkedHashMap<>();
        values.entrySet().stream()
                .filter(entry -> !isEmpty(entry.getKey()) && entry.getValue() != null)
                .sorted(Map.Entry.<String, MoviePlaybackPreference>comparingByValue(
                        Comparator.comparingLong(MoviePlaybackPreference::updatedAt)).reversed())
                .limit(MAX_ENTRIES)
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    private static LinkedHashMap<String, MoviePlaybackPreference> read() {
        try {
            String json = Prefers.getString(KEY, "");
            LinkedHashMap<String, MoviePlaybackPreference> values = App.gson().fromJson(json, TYPE);
            return values == null ? new LinkedHashMap<>() : values;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
