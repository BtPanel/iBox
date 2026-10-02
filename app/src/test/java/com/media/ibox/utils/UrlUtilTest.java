package com.media.ibox.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UrlUtilTest {

    @Test
    public void replacesAnotherAppProxyPort() {
        String url = "http://127.0.0.1:9979/proxy?do=m3u8&url=test";

        assertEquals("http://127.0.0.1:9980/proxy?do=m3u8&url=test", UrlUtil.normalizeLocalProxyPort(url, 9980));
    }

    @Test
    public void replacesEncodedNestedProxyPort() {
        String url = "https://example.com/danmaku?url=http%3A%2F%2F127.0.0.1%3A9979%2Fproxy%3Fdo%3Ddanmu";

        assertEquals(
                "https://example.com/danmaku?url=http%3A%2F%2F127.0.0.1%3A9980%2Fproxy%3Fdo%3Ddanmu",
                UrlUtil.normalizeLocalProxyPort(url, 9980));
    }

    @Test
    public void leavesUnrelatedLocalAddressesUnchanged() {
        assertEquals("http://127.0.0.1:9979/cache?do=get", UrlUtil.normalizeLocalProxyPort("http://127.0.0.1:9979/cache?do=get", 9980));
        assertEquals("http://localhost:9979/proxy?do=m3u8", UrlUtil.normalizeLocalProxyPort("http://localhost:9979/proxy?do=m3u8", 9980));
        assertEquals("http://127.0.0.1:9999/proxy?do=m3u8", UrlUtil.normalizeLocalProxyPort("http://127.0.0.1:9999/proxy?do=m3u8", 9980));
    }

    @Test
    public void leavesAddressUnchangedBeforeServerStarts() {
        String url = "http://127.0.0.1:9979/proxy?do=m3u8";

        assertEquals(url, UrlUtil.normalizeLocalProxyPort(url, -1));
    }
}
