package com.media.ibox.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RemoteAccessQrTest {

    @Test
    public void qrContainsOnlyBaseAddress() {
        assertEquals("http://192.168.1.4:9978", RemoteAccessQr.url(true, "http://192.168.1.4:9978"));
        assertNull(RemoteAccessQr.url(false, "http://192.168.1.4:9978"));
        assertNull(RemoteAccessQr.url(true, "http://192.168.1.4:9978/?pin=123456"));
        assertNull(RemoteAccessQr.url(true, "http://127.0.0.1:9978"));
        assertNull(RemoteAccessQr.url(true, "http://0.0.0.0:9978"));
    }
}
