package com.imedia.box.offline;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DownloadSpeedTracker {

    private static final long MIN_SAMPLE_INTERVAL_MS = 500;

    private final Map<String, Sample> samples = new HashMap<>();
    private final Map<String, Long> speeds = new HashMap<>();

    public long update(String id, long bytes, long nowMs) {
        Sample previous = samples.get(id);
        if (previous == null || bytes < previous.bytes()) {
            samples.put(id, new Sample(bytes, nowMs));
            speeds.put(id, 0L);
            return 0;
        }
        long elapsedMs = nowMs - previous.atMs();
        if (elapsedMs < MIN_SAMPLE_INTERVAL_MS) return speeds.getOrDefault(id, 0L);
        long speed = Math.max(0, bytes - previous.bytes()) * 1000 / Math.max(elapsedMs, 1);
        samples.put(id, new Sample(bytes, nowMs));
        speeds.put(id, speed);
        return speed;
    }

    public void retain(Set<String> ids) {
        samples.keySet().retainAll(ids);
        speeds.keySet().retainAll(ids);
    }

    private record Sample(long bytes, long atMs) {
    }
}
