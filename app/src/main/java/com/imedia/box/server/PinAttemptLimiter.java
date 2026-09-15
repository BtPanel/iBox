package com.imedia.box.server;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class PinAttemptLimiter {

    private final Map<String, ArrayDeque<Long>> failures = new HashMap<>();
    private final int maxFailures;
    private final long windowMs;

    PinAttemptLimiter(int maxFailures, long windowMs) {
        this.maxFailures = maxFailures;
        this.windowMs = windowMs;
    }

    synchronized boolean isAllowed(String client, long now) {
        ArrayDeque<Long> attempts = failures.get(client);
        if (attempts == null) return true;
        prune(attempts, now);
        if (attempts.isEmpty()) failures.remove(client);
        return attempts.size() < maxFailures;
    }

    synchronized void recordFailure(String client, long now) {
        ArrayDeque<Long> attempts = failures.computeIfAbsent(client, key -> new ArrayDeque<>());
        prune(attempts, now);
        attempts.addLast(now);
    }

    synchronized void recordSuccess(String client) {
        failures.remove(client);
    }

    synchronized void clear() {
        failures.clear();
    }

    private void prune(ArrayDeque<Long> attempts, long now) {
        long cutoff = now - windowMs;
        while (!attempts.isEmpty() && attempts.peekFirst() <= cutoff) attempts.removeFirst();
    }
}
