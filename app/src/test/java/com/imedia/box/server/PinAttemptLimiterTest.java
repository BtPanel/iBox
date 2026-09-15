package com.imedia.box.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinAttemptLimiterTest {

    @Test
    public void blocksClientAfterFiveFailuresWithinWindow() {
        PinAttemptLimiter limiter = new PinAttemptLimiter(5, 300_000);
        long now = 1_000_000;
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.isAllowed("192.168.1.8", now));
            limiter.recordFailure("192.168.1.8", now);
        }
        assertFalse(limiter.isAllowed("192.168.1.8", now));
        assertTrue(limiter.isAllowed("192.168.1.9", now));
    }

    @Test
    public void windowExpiryAndSuccessClearFailures() {
        PinAttemptLimiter limiter = new PinAttemptLimiter(2, 1_000);
        limiter.recordFailure("client", 100);
        limiter.recordFailure("client", 200);
        assertFalse(limiter.isAllowed("client", 500));
        assertTrue(limiter.isAllowed("client", 1_201));
        limiter.recordFailure("client", 1_300);
        limiter.recordSuccess("client");
        assertTrue(limiter.isAllowed("client", 1_300));
    }
}
