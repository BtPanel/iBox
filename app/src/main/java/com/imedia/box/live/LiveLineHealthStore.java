package com.imedia.box.live;

import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imedia.box.bean.Channel;
import com.imedia.box.playback.live.LiveFailureKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class LiveLineHealthStore {

    private static final int VERSION = 2;
    private static final int MAX_ENTRIES = 500;
    private static final long EXPIRY_MS = TimeUnit.DAYS.toMillis(30);
    private static final Gson GSON = new Gson();

    private final String key;
    private final Backend backend;
    private final LongSupplier clock;
    private final Map<LiveLineKey, LiveLineHealth> entries;

    public LiveLineHealthStore() {
        this(LiveChannelStore.sourceKey(), new Backend() {
            @Override
            public String get(String key) {
                return Prefers.getString(key);
            }

            @Override
            public void put(String key, String value) {
                Prefers.put(key, value);
            }
        }, System::currentTimeMillis);
    }

    public static String currentSourceKey() {
        return LiveChannelStore.sourceKey();
    }

    LiveLineHealthStore(String sourceKey, Backend backend, LongSupplier clock) {
        this.key = "live_line_health_v2_" + (sourceKey == null ? "" : sourceKey);
        this.backend = backend;
        this.clock = clock;
        this.entries = load();
    }

    public Map<LiveLineKey, LiveLineHealth> current() {
        return Map.copyOf(entries);
    }

    public List<Integer> rank(Channel channel, Set<Integer> excluded) {
        if (channel == null) return List.of();
        Set<Integer> attempted = excluded == null ? Set.of() : excluded;
        List<Candidate> candidates = new ArrayList<>();
        long nowMs = clock.getAsLong();
        for (int index = 0; index < channel.getUrls().size(); index++) {
            if (attempted.contains(index)) continue;
            LiveLineHealth health = entries.get(key(channel, index));
            candidates.add(new Candidate(index, health == null ? LiveLineHealth.empty() : health));
        }
        if (candidates.stream().anyMatch(candidate -> !candidate.health().isCoolingDown(nowMs))) {
            candidates.removeIf(candidate -> candidate.health().isCoolingDown(nowMs));
        }
        candidates.sort(Comparator.comparingInt((Candidate candidate) -> candidate.health().score()).reversed()
                .thenComparingInt(Candidate::index));
        return candidates.stream().map(Candidate::index).toList();
    }

    public void recordFailure(Channel channel, int index, LiveFailureKind kind) {
        LiveLineKey lineKey = key(channel, index);
        if (lineKey == null) return;
        entries.put(lineKey, entries.getOrDefault(lineKey, LiveLineHealth.empty()).recordFailure(kind, clock.getAsLong()));
        save();
    }

    public void recordSuccess(Channel channel, int index, long startupMs) {
        LiveLineKey lineKey = key(channel, index);
        if (lineKey == null) return;
        entries.put(lineKey, entries.getOrDefault(lineKey, LiveLineHealth.empty()).recordSuccess(startupMs, clock.getAsLong()));
        save();
    }

    private LiveLineKey key(Channel channel, int index) {
        if (channel == null || index < 0 || index >= channel.getUrls().size()) return null;
        LiveChannelStore.ChannelRef ref = LiveChannelStore.resolve(channel);
        if (ref == null) return null;
        return LiveLineKey.create(sourceKey(), ref.group(), ref.channel().getName(), channel.getUrls().get(index));
    }

    private String sourceKey() {
        return key.substring("live_line_health_v2_".length());
    }

    private Map<LiveLineKey, LiveLineHealth> load() {
        Map<LiveLineKey, LiveLineHealth> result = new LinkedHashMap<>();
        try {
            JsonObject envelope = JsonParser.parseString(backend.get(key)).getAsJsonObject();
            if (!envelope.has("version") || envelope.get("version").getAsInt() != VERSION) return result;
            JsonArray values = envelope.has("entries") && envelope.get("entries").isJsonArray() ? envelope.getAsJsonArray("entries") : new JsonArray();
            for (JsonElement value : values) loadEntry(value, result);
        } catch (Exception ignored) {
        }
        return result;
    }

    private void loadEntry(JsonElement value, Map<LiveLineKey, LiveLineHealth> result) {
        try {
            if (value == null || value.isJsonNull()) return;
            Entry entry = GSON.fromJson(value, Entry.class);
            if (entry == null || entry.key() == null || !valid(entry.health())) return;
            result.put(entry.key(), entry.health());
        } catch (Exception ignored) {
        }
    }

    private boolean valid(LiveLineHealth health) {
        return health != null && health.successCount() >= 0 && health.failureCount() >= 0
                && health.consecutiveFailures() >= 0 && health.lastSuccessMs() >= 0
                && health.lastFailureMs() >= 0 && health.averageStartupMs() >= 0 && health.cooldownUntilMs() >= 0;
    }

    private void save() {
        long cutoff = clock.getAsLong() - EXPIRY_MS;
        entries.entrySet().removeIf(entry -> entry.getValue().lastActivityMs() < cutoff);
        if (entries.size() > MAX_ENTRIES) {
            List<Map.Entry<LiveLineKey, LiveLineHealth>> oldest = new ArrayList<>(entries.entrySet());
            oldest.sort(Comparator.comparingLong(entry -> entry.getValue().lastActivityMs()));
            for (int index = 0; index < oldest.size() - MAX_ENTRIES; index++) entries.remove(oldest.get(index).getKey());
        }
        List<Entry> values = entries.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList();
        backend.put(key, GSON.toJson(new Envelope(VERSION, values)));
    }

    interface Backend {
        String get(String key);

        void put(String key, String value);
    }

    record Envelope(int version, List<Entry> entries) {
    }

    record Entry(LiveLineKey key, LiveLineHealth health) {
    }

    private record Candidate(int index, LiveLineHealth health) {
    }
}
