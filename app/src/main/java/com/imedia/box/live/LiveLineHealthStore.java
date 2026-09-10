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
import java.util.Locale;
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
        prune();
        return Map.copyOf(entries);
    }

    public List<Integer> rank(Channel channel, Set<Integer> excluded) {
        prune();
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
        prune();
        LiveLineKey lineKey = key(channel, index);
        if (lineKey == null) return;
        entries.put(lineKey, entries.getOrDefault(lineKey, LiveLineHealth.empty()).recordFailure(kind, clock.getAsLong()));
        save();
    }

    public void recordSuccess(Channel channel, int index, long startupMs) {
        prune();
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
            if (number(envelope, "version") != VERSION) return result;
            JsonArray values = envelope.has("entries") && envelope.get("entries").isJsonArray() ? envelope.getAsJsonArray("entries") : new JsonArray();
            for (JsonElement value : values) loadEntry(value, result);
        } catch (Exception ignored) {
        }
        return result;
    }

    private void loadEntry(JsonElement value, Map<LiveLineKey, LiveLineHealth> result) {
        try {
            JsonObject entry = value.getAsJsonObject();
            JsonObject key = entry.getAsJsonObject("key");
            String source = string(key, "sourceKey");
            String group = string(key, "group");
            String channel = string(key, "channel");
            String digest = string(key, "urlDigest").toLowerCase(Locale.ROOT);
            if (!source.equals(sourceKey()) || group.isEmpty() || channel.isEmpty()
                    || !digest.matches("[0-9a-f]{64}")) return;
            JsonObject health = entry.getAsJsonObject("health");
            LiveLineHealth parsed = new LiveLineHealth(count(health, "successCount"), count(health, "failureCount"),
                    count(health, "consecutiveFailures"), number(health, "lastSuccessMs"),
                    number(health, "lastFailureMs"), number(health, "averageStartupMs"), number(health, "cooldownUntilMs"));
            long nowMs = clock.getAsLong();
            if (parsed.lastSuccessMs() > nowMs || parsed.lastFailureMs() > nowMs) return;
            if (parsed.cooldownUntilMs() != 0 && (parsed.lastFailureMs() == 0
                    || parsed.cooldownUntilMs() < parsed.lastFailureMs()
                    || parsed.cooldownUntilMs() - parsed.lastFailureMs() > TimeUnit.HOURS.toMillis(24))) return;
            result.put(new LiveLineKey(source, group, channel, digest), parsed);
        } catch (Exception ignored) {
        }
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(field);
        return value.getAsString();
    }

    private static long number(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(field);
        long number = value.getAsBigDecimal().longValueExact();
        if (number < 0) throw new IllegalArgumentException(field);
        return number;
    }

    private static int count(JsonObject object, String field) {
        long number = number(object, field);
        if (number > 20) throw new IllegalArgumentException(field);
        return (int) number;
    }

    private void prune() {
        long cutoff = clock.getAsLong() - EXPIRY_MS;
        entries.entrySet().removeIf(entry -> entry.getValue().lastActivityMs() < cutoff);
        if (entries.size() > MAX_ENTRIES) {
            List<Map.Entry<LiveLineKey, LiveLineHealth>> oldest = new ArrayList<>(entries.entrySet());
            oldest.sort(Comparator.comparingLong(entry -> entry.getValue().lastActivityMs()));
            for (int index = 0; index < oldest.size() - MAX_ENTRIES; index++) entries.remove(oldest.get(index).getKey());
        }
    }

    private void save() {
        prune();
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
