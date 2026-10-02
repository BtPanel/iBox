package com.media.ibox.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PairingSessionStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final long lifetimeMs;

    PairingSessionStore(long lifetimeMs) {
        this.lifetimeMs = lifetimeMs;
    }

    String create(String client, long now) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(client, now + lifetimeMs));
        return token;
    }

    boolean isValid(String token, String client, long now) {
        if (token == null || token.isEmpty()) return false;
        Session session = sessions.get(token);
        if (session == null) return false;
        if (session.expiresAt <= now) {
            sessions.remove(token, session);
            return false;
        }
        return session.client.equals(client);
    }

    void clear() {
        sessions.clear();
    }

    private record Session(String client, long expiresAt) {
    }
}
