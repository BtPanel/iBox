package com.imedia.box.dlna;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CastConnectionHealthTest {

    @Test
    public void transientFailuresDoNotMarkTheDeviceOffline() {
        CastConnectionHealth health = new CastConnectionHealth();

        health.pollFailed();
        health.pollFailed();
        assertEquals(CastConnectionHealth.Status.CONNECTED, health.status());

        health.pollSucceeded();
        health.pollFailed();
        health.pollFailed();
        assertEquals(CastConnectionHealth.Status.CONNECTED, health.status());

        health.pollFailed();
        assertEquals(CastConnectionHealth.Status.LOST, health.status());
    }

    @Test
    public void successfulPollRecoversAConnectionWithoutRecasting() {
        CastConnectionHealth health = new CastConnectionHealth();
        health.pollFailed();
        health.pollFailed();
        health.pollFailed();

        health.pollSucceeded();

        assertEquals(CastConnectionHealth.Status.CONNECTED, health.status());
    }

    @Test
    public void retryIsSingleFlightAndCanBeTriedAgainAfterFailure() {
        CastConnectionHealth health = new CastConnectionHealth();
        health.pollFailed();
        health.pollFailed();
        health.pollFailed();

        assertTrue(health.beginRetry());
        assertEquals(CastConnectionHealth.Status.RETRYING, health.status());
        assertFalse(health.beginRetry());

        health.retryFailed();
        assertEquals(CastConnectionHealth.Status.LOST, health.status());
        assertTrue(health.beginRetry());
        health.retrySucceeded();
        assertEquals(CastConnectionHealth.Status.CONNECTED, health.status());
    }

    @Test
    public void newSessionClearsOldFailureState() {
        CastConnectionHealth health = new CastConnectionHealth();
        health.pollFailed();
        health.pollFailed();
        health.pollFailed();

        health.reset();

        assertEquals(CastConnectionHealth.Status.CONNECTED, health.status());
        assertFalse(health.beginRetry());
    }
}
