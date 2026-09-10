package com.imedia.box.live;

import com.imedia.box.playback.live.LiveFailureKind;

import java.util.concurrent.TimeUnit;

public record LiveLineHealth(
        int successCount,
        int failureCount,
        int consecutiveFailures,
        long lastSuccessMs,
        long lastFailureMs,
        long averageStartupMs,
        long cooldownUntilMs) {

    public static LiveLineHealth empty() {
        return new LiveLineHealth(0, 0, 0, 0, 0, 0, 0);
    }

    public int score() {
        int startupPenalty = averageStartupMs <= 2_000 ? 0 : (int) Math.min(20, (averageStartupMs - 2_000) / 1_000);
        return 100 + Math.min(successCount, 5) * 4 - Math.min(failureCount, 5) * 4
                - Math.min(consecutiveFailures, 3) * 25 - startupPenalty;
    }

    public boolean isCoolingDown(long nowMs) {
        return cooldownUntilMs > nowMs;
    }

    public long lastActivityMs() {
        return Math.max(lastSuccessMs, lastFailureMs);
    }

    public LiveLineHealth recordFailure(LiveFailureKind kind, long nowMs) {
        int nextConsecutive = Math.min(20, consecutiveFailures + 1);
        long baseMs = switch (kind == null ? LiveFailureKind.PLAYBACK : kind) {
            case SOURCE -> TimeUnit.MINUTES.toMillis(30);
            case NETWORK, TIMEOUT -> TimeUnit.MINUTES.toMillis(2);
            case DECODE -> TimeUnit.MINUTES.toMillis(10);
            case PLAYBACK -> TimeUnit.MINUTES.toMillis(5);
        };
        int exponent = Math.min(nextConsecutive - 1, 3);
        long cooldownMs = Math.min(TimeUnit.HOURS.toMillis(24), baseMs * (1L << exponent));
        return new LiveLineHealth(successCount, Math.min(20, failureCount + 1), nextConsecutive,
                lastSuccessMs, nowMs, averageStartupMs, nowMs + cooldownMs);
    }

    public LiveLineHealth recordSuccess(long startupMs, long nowMs) {
        long sample = Math.max(0, startupMs);
        long average = averageStartupMs == 0 ? sample : Math.round(averageStartupMs * 0.7d + sample * 0.3d);
        return new LiveLineHealth(Math.min(20, successCount + 1), failureCount, 0,
                nowMs, lastFailureMs, average, 0);
    }
}
