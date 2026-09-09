package com.imedia.box.diagnostics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SourceHealthAddressTest {

    @Test
    public void removesCredentialsQueryAndFragment() {
        assertEquals("https://example.com/path/config.json",
                SourceHealthAddress.sanitize("https://user:pass@example.com/path/config.json?token=abc#part"));
    }

    @Test
    public void keepsPortAndHidesLocalPath() {
        assertEquals("http://example.com:8080/config", SourceHealthAddress.sanitize("http://example.com:8080/config?q=1"));
        assertEquals("file://local", SourceHealthAddress.sanitize("file:///storage/emulated/0/private/config.json"));
        assertEquals("content://local", SourceHealthAddress.sanitize("content://provider/private/config.json"));
    }

    @Test
    public void hidesUnsupportedOrMalformedValues() {
        assertEquals("", SourceHealthAddress.sanitize("ftp://user:pass@example.com/config"));
        assertEquals("", SourceHealthAddress.sanitize("not a url?token=secret"));
        assertEquals("", SourceHealthAddress.sanitize(null));
    }
}
