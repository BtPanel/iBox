package com.media.ibox.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.media.ibox.playback.live.LiveFailureKind;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class LiveLineHealthTest {

    @Test
    public void scoreUsesBoundedCountsConsecutiveFailuresAndStartupPenalty() {
        LiveLineHealth health = new LiveLineHealth(5, 5, 2, 0, 0, 12_000, 0);

        assertEquals(40, health.score());
    }

    @Test
    public void sourceCooldownDoublesAndCapsExponent() {
        LiveLineHealth health = LiveLineHealth.empty();
        health = health.recordFailure(LiveFailureKind.SOURCE, 1_000);
        assertEquals(1_000 + TimeUnit.MINUTES.toMillis(30), health.cooldownUntilMs());
        for (int i = 0; i < 5; i++) health = health.recordFailure(LiveFailureKind.SOURCE, 1_000);

        assertEquals(1_000 + TimeUnit.HOURS.toMillis(4), health.cooldownUntilMs());
    }

    @Test
    public void countsCapAtTwentyAndSuccessUsesEmaAndClearsCooldown() {
        LiveLineHealth health = LiveLineHealth.empty();
        for (int i = 0; i < 21; i++) health = health.recordFailure(LiveFailureKind.NETWORK, 100);
        health = health.recordSuccess(1_000, 200).recordSuccess(3_000, 300);

        assertEquals(20, health.failureCount());
        assertEquals(2, health.successCount());
        assertEquals(0, health.consecutiveFailures());
        assertEquals(1_600, health.averageStartupMs());
        assertFalse(health.isCoolingDown(300));
        assertTrue(health.lastActivityMs() == 300);
    }
}
