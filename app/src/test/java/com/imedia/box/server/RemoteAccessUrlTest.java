package com.imedia.box.server;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

public class RemoteAccessUrlTest {

    @Test
    public void appendsStreamTokenToPlainAndParameterizedUrls() {
        assertEquals("http://box/file/a?stream_token=abc", RemoteAccess.appendQueryParameter("http://box/file/a", "stream_token", "abc"));
        assertEquals("http://box/proxy?do=js&stream_token=abc", RemoteAccess.appendQueryParameter("http://box/proxy?do=js", "stream_token", "abc"));
    }

    @Test
    public void keepsFragmentAtEnd() {
        assertEquals("http://box/file/a?stream_token=abc#part", RemoteAccess.appendQueryParameter("http://box/file/a#part", "stream_token", "abc"));
    }

    @Test
    public void prefersIBoxHeadersAndFallsBackToLegacyClients() {
        String oldAccess = "x-i" + "media-token";
        String oldStream = "x-i" + "media-stream-token";

        assertEquals("new", RemoteAccess.tokenHeader(Map.of("x-ibox-token", "new", oldAccess, "old"), false));
        assertEquals("old", RemoteAccess.tokenHeader(Map.of(oldAccess, "old"), false));
        assertEquals("stream", RemoteAccess.tokenHeader(Map.of("x-ibox-stream-token", "stream"), true));
        assertEquals("old-stream", RemoteAccess.tokenHeader(Map.of(oldStream, "old-stream"), true));
    }

    @Test
    public void readsNewAndLegacyPairingCookies() {
        assertEquals("new", RemoteAccess.cookieValue("ibox_pair=new"));
        assertEquals("old", RemoteAccess.cookieValue("i" + "media_pair=old"));
        assertEquals("new", RemoteAccess.cookieValue("i" + "media_pair=old; ibox_pair=new"));
        assertEquals("new", RemoteAccess.cookieValue("ibox_pair=new; i" + "media_pair=old"));
    }
}
