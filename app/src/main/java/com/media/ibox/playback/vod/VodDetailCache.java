package com.media.ibox.playback.vod;

import androidx.annotation.Nullable;

import com.media.ibox.bean.Result;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VodDetailCache {

    private static final long MAX_AGE_MS = 30_000L;
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private VodDetailCache() {
    }

    public static void put(String key, String id, Result result) {
        long now = System.currentTimeMillis();
        ENTRIES.entrySet().removeIf(entry -> now - entry.getValue().createdAt > MAX_AGE_MS);
        ENTRIES.put(cacheKey(key, id), new Entry(result, now));
    }

    @Nullable
    public static Result take(String key, String id) {
        Entry entry = ENTRIES.remove(cacheKey(key, id));
        if (entry == null || System.currentTimeMillis() - entry.createdAt > MAX_AGE_MS) return null;
        return entry.result;
    }

    private static String cacheKey(String key, String id) {
        return key + '\u0000' + id;
    }

    private record Entry(Result result, long createdAt) {
    }
}
