package com.imedia.box.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServerAccessPolicyTest {

    @Test
    public void keepsParserAndCrawlerCacheOnLoopback() {
        assertTrue(ServerAccessPolicy.isLoopbackOnly("/cache?do=get"));
        assertTrue(ServerAccessPolicy.isLoopbackOnly("/parse?url=test"));
        assertFalse(ServerAccessPolicy.isLoopbackOnly("/cache-file"));
    }

    @Test
    public void requiresCapabilitiesForStreamEndpoints() {
        assertTrue(ServerAccessPolicy.isStreamRequest("/file/video.mp4"));
        assertTrue(ServerAccessPolicy.isStreamRequest("/proxy?do=js"));
        assertTrue(ServerAccessPolicy.isStreamRequest("/tvbus"));
        assertFalse(ServerAccessPolicy.isStreamRequest("/media"));
    }

    @Test
    public void protectsRemoteStateAndManagementEndpoints() {
        assertTrue(ServerAccessPolicy.requiresRemoteAuthorization("/media"));
        assertTrue(ServerAccessPolicy.requiresRemoteAuthorization("/action?do=control"));
        assertTrue(ServerAccessPolicy.requiresRemoteAuthorization("/manage"));
        assertTrue(ServerAccessPolicy.requiresRemoteAuthorization("/index.html"));
        assertFalse(ServerAccessPolicy.requiresRemoteAuthorization("/favicon.ico"));
        assertFalse(ServerAccessPolicy.matchesEndpoint("/action-anything", "/action"));
    }
}
