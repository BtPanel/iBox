package com.imedia.box.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageFailureBackoffTest {

    private static final String URL = "https://example.com/poster.jpg";

    @Test
    public void posterFailureIsRetriedAfterDefaultShortBackoff() {
        ImageFailureBackoff backoff = new ImageFailureBackoff();

        backoff.recordFailure(URL, 1_000L);

        assertTrue(backoff.shouldDefer(URL, 15_999L));
        assertFalse(backoff.shouldDefer(URL, 16_000L));
    }

    @Test
    public void successfulLoadImmediatelyClearsFailure() {
        ImageFailureBackoff backoff = new ImageFailureBackoff(256, 15_000L);
        backoff.recordFailure(URL, 1_000L);

        backoff.recordSuccess(URL);

        assertFalse(backoff.shouldDefer(URL, 1_001L));
    }

    @Test
    public void oldestFailureIsEvictedAtCapacity() {
        ImageFailureBackoff backoff = new ImageFailureBackoff(2, 15_000L);
        backoff.recordFailure("https://example.com/first.jpg", 1_000L);
        backoff.recordFailure("https://example.com/second.jpg", 1_001L);

        backoff.recordFailure("https://example.com/third.jpg", 1_002L);

        assertFalse(backoff.shouldDefer("https://example.com/first.jpg", 1_003L));
        assertTrue(backoff.shouldDefer("https://example.com/second.jpg", 1_003L));
        assertTrue(backoff.shouldDefer("https://example.com/third.jpg", 1_003L));
    }
}
