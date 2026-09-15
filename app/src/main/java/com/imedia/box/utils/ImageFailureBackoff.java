package com.imedia.box.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ImageFailureBackoff {

    private static final int POSTER_MAX_FAILURES = 256;
    private static final long POSTER_BACKOFF_MS = TimeUnit.SECONDS.toMillis(15);

    private final long backoffMillis;
    private final Map<String, Long> failures;

    ImageFailureBackoff() {
        this(POSTER_MAX_FAILURES, POSTER_BACKOFF_MS);
    }

    ImageFailureBackoff(int maxEntries, long backoffMillis) {
        this.backoffMillis = backoffMillis;
        this.failures = new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > maxEntries;
            }
        };
    }

    synchronized void recordFailure(String url, long now) {
        failures.put(url, now);
    }

    synchronized void recordSuccess(String url) {
        failures.remove(url);
    }

    synchronized boolean shouldDefer(String url, long now) {
        Long failedAt = failures.get(url);
        if (failedAt == null) return false;
        if (now - failedAt < backoffMillis) return true;
        failures.remove(url);
        return false;
    }
}
