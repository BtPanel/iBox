package com.imedia.box.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PairingSessionStoreTest {

    @Test
    public void sessionIsBoundToClientAndExpires() {
        PairingSessionStore sessions = new PairingSessionStore(1_000);
        String token = sessions.create("192.168.1.8", 100);
        assertTrue(sessions.isValid(token, "192.168.1.8", 1_099));
        assertFalse(sessions.isValid(token, "192.168.1.9", 1_099));
        assertFalse(sessions.isValid(token, "192.168.1.8", 1_100));
    }

    @Test
    public void clearRevokesAllSessions() {
        PairingSessionStore sessions = new PairingSessionStore(1_000);
        String token = sessions.create("client", 100);
        sessions.clear();
        assertFalse(sessions.isValid(token, "client", 101));
    }
}
